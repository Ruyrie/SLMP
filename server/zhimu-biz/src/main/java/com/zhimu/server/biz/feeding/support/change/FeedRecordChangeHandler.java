package com.zhimu.server.biz.feeding.support.change;

import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeBizTypes;
import com.zhimu.server.biz.common.change.ChangeCardItem;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeRequestHandler;
import com.zhimu.server.biz.common.change.ChangeRequestJson;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.feeding.entity.FeedRecord;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedRecordMapper;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.feeding.support.FeedCalc;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.OptionVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 投喂记录（FEED_RECORD，biz_id = 投喂记录 ID，record_version = 投喂记录 version）。
 * <p>
 * 字段与 App Mock（change_form_feed_record.json）一致：feed_id 饲料（本场饲料选项）、actual_qty 实际投喂量
 * （千克，0–1000，辅助文字“计划 160 千克”），支持 VOID 作废。
 * 登记日期早于今天的记录走修改申请；审核通过重算偏差与异常、按新值重新扣减饲料库存、按补登期限恢复任务状态。
 */
@Component
@RequiredArgsConstructor
public class FeedRecordChangeHandler implements ChangeRequestHandler {

    private static final String FIELD_FEED_ID = "feed_id";
    private static final String FIELD_ACTUAL_QTY = "actual_qty";
    private static final String UNIT = "千克";
    private static final BigDecimal MAX_QTY = new BigDecimal("1000");
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.1000");

    private final FeedingChangeSupport change;
    private final FeedRecordMapper feedRecordMapper;
    private final FeedTaskMapper feedTaskMapper;
    private final FeedStockPort feedStockPort;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.FEED_RECORD);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.FEED_RECORD);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        FeedRecord record = change.feedRecord(bizId);
        if (record == null) {
            return null;
        }
        BatchInfo batch = change.batch(record.getBatchId());
        String batchNo = batch == null ? "" : batch.batchNo();
        String penName = change.penName(record.getPenId());
        String date = change.monthDay(record.getFeedDate());
        String meal = record.getMealName() == null ? "" : record.getMealName();
        String locator = penName + " · " + batchNo + " · " + date + " " + meal;
        String title = "投喂记录 · " + penName;
        String subtitle = batchNo + " · " + date + " " + meal + " · "
                + change.userName(record.getCreatedBy()) + "登记";
        int version = record.getVersion() == null ? 0 : record.getVersion();
        return new ChangeRecord(BizType.FEED_RECORD, record.getId(), record.getFarmId(), record.getBatchId(),
                record.getPenId(), locator, title, subtitle, String.valueOf(version), false,
                "VOID".equalsIgnoreCase(record.getStatus()));
    }

    @Override
    public boolean requiresRequest(ChangeRecord record) {
        FeedRecord fresh = change.feedRecord(record.bizId());
        if (fresh == null) {
            return true;
        }
        return !BeijingTime.today().equals(fresh.getFeedDate());
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        FeedRecord fresh = change.feedRecord(record.bizId());
        if (fresh == null) {
            throw notFound();
        }
        String feedName = change.feedName(fresh.getFarmId(), fresh.getFeedId());
        BigDecimal actual = fresh.getActualQty() == null ? BigDecimal.ZERO : fresh.getActualQty();
        BigDecimal plan = fresh.getPlanQty() == null ? BigDecimal.ZERO : fresh.getPlanQty();
        List<OptionVO> options = new ArrayList<>();
        for (FeedStockPort.FeedBrief feed : change.feedOptions(fresh.getFarmId())) {
            if (feed == null || !feed.selectable()) {
                continue;
            }
            options.add(OptionVO.of(String.valueOf(feed.feedId()), feed.feedName()));
        }
        ChangeField feedField = new ChangeField(FIELD_FEED_ID, "饲料", feedName, null, ChangeField.SELECT,
                options, null, null, List.of("饲料成本", "经营报表"), null);
        ChangeField qtyField = new ChangeField(FIELD_ACTUAL_QTY, "实际投喂量",
                FeedingChangeSupport.plain(actual), UNIT, ChangeField.NUMBER, List.of(), BigDecimal.ZERO, MAX_QTY,
                List.of("头均采食量", "料肉比", "饲料成本", "经营报表"),
                "计划 " + FeedingChangeSupport.plain(plan) + " " + UNIT);
        List<ChangeCardItem> card = List.of(
                new ChangeCardItem("饲料", feedName),
                new ChangeCardItem("实际投喂量", FeedingChangeSupport.display(FeedingChangeSupport.plain(actual), UNIT)),
                new ChangeCardItem("计划量", FeedingChangeSupport.display(FeedingChangeSupport.plain(plan), UNIT)));
        String meal = fresh.getMealName() == null ? "" : fresh.getMealName();
        String deadlineText = backfillDeadlineText(fresh);
        String voidNotice = "审核通过后这条投喂记录作废，" + FeedingChangeSupport.plain(actual) + " " + UNIT
                + feedName + "退回库存；" + change.monthDay(fresh.getFeedDate()) + " " + meal
                + "的饲喂任务在补登期限内" + deadlineText + "恢复为待投喂，超过期限记为未执行。";
        return new ChangeForm(card, List.of(feedField, qtyField),
                List.of("修改实际投喂量会同步调整饲料库存出库数量"),
                "这条记录会影响饲料库存和成本统计，需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。",
                false, true, voidNotice, List.of("库存（退回饲料）", "成本", "统计"));
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType == ChangeType.VOID) {
            return ChangeValidation.withoutField(
                    List.of(ChangeImpact.INVENTORY, ChangeImpact.COST, ChangeImpact.STATS),
                    List.of("库存（退回饲料）", "成本", "统计"));
        }
        if (changeType == ChangeType.DELETE) {
            throw new BizException(com.zhimu.server.common.error.ErrorCode.STATE_NOT_ALLOWED,
                    "这条投喂记录暂不能删除");
        }
        FeedingChangeSupport.requireField(form, fieldCode);
        FeedRecord fresh = change.feedRecord(record.bizId());
        if (fresh == null) {
            throw notFound();
        }
        if (!"NORMAL".equals(fresh.getStatus())) {
            throw new BizException(com.zhimu.server.common.error.ErrorCode.STATE_CHANGED, "这条投喂记录已作废");
        }
        BigDecimal actual = fresh.getActualQty() == null ? BigDecimal.ZERO : fresh.getActualQty();
        if (FIELD_FEED_ID.equals(fieldCode)) {
            String text = newValue == null ? "" : newValue.trim();
            String selectedId = null;
            String selectedName = null;
            for (OptionVO option : form.fieldOf(FIELD_FEED_ID).options()) {
                if (text.equals(option.getValue()) || text.equals(option.getLabel())) {
                    selectedId = option.getValue();
                    selectedName = option.getLabel();
                    break;
                }
            }
            if (selectedId == null) {
                throw FeedingChangeSupport.fieldError("new_value", "请选择饲料");
            }
            String beforeName = change.feedName(fresh.getFarmId(), fresh.getFeedId());
            if (selectedId.equals(String.valueOf(fresh.getFeedId()))) {
                FeedingChangeSupport.sameValue();
            }
            FeedStockPort.FeedBrief feed = change.feed(fresh.getFarmId(), Long.valueOf(selectedId));
            if (feed != null && feed.stockQty() != null && feed.stockQty().compareTo(actual) < 0) {
                throw stockNotEnough(feed.feedName(), feed.stockQty(), actual);
            }
            return ChangeValidation.of(FIELD_FEED_ID, "饲料", null, String.valueOf(fresh.getFeedId()), beforeName,
                    selectedId, selectedName, List.of(ChangeImpact.INVENTORY, ChangeImpact.COST),
                    List.of("饲料成本", "经营报表"));
        }
        if (!FIELD_ACTUAL_QTY.equals(fieldCode)) {
            throw FeedingChangeSupport.fieldError("field_code", "请选择要修改的项目");
        }
        BigDecimal after = FeedingChangeSupport.decimalValue(newValue, "实际投喂量", MAX_QTY, UNIT);
        if (after.compareTo(actual) == 0) {
            FeedingChangeSupport.sameValue();
        }
        BigDecimal delta = after.subtract(actual);
        if (delta.signum() > 0) {
            FeedStockPort.FeedBrief feed = change.feed(fresh.getFarmId(), fresh.getFeedId());
            if (feed != null && feed.stockQty() != null && feed.stockQty().compareTo(delta) < 0) {
                throw stockNotEnough(feed.feedName(), feed.stockQty(), delta);
            }
        }
        return ChangeValidation.of(FIELD_ACTUAL_QTY, "实际投喂量", UNIT, FeedingChangeSupport.plain(actual),
                FeedingChangeSupport.display(FeedingChangeSupport.plain(actual), UNIT),
                FeedingChangeSupport.plain(after), FeedingChangeSupport.display(FeedingChangeSupport.plain(after), UNIT),
                List.of(ChangeImpact.INVENTORY, ChangeImpact.COST, ChangeImpact.STATS),
                List.of("头均采食量", "料肉比", "饲料成本", "经营报表"));
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        FeedRecord fresh = change.feedRecord(record.bizId());
        if (fresh == null) {
            throw notFound();
        }
        if (!"NORMAL".equals(fresh.getStatus())) {
            throw new BizException(com.zhimu.server.common.error.ErrorCode.STATE_CHANGED, "这条投喂记录已作废");
        }
        if (ChangeType.VOID.name().equals(request.getChangeType())) {
            return;
        }
        Long targetFeedId = targetFeedId(fresh, request);
        BigDecimal need = needQty(fresh, request);
        FeedStockPort.FeedBrief feed = change.feed(fresh.getFarmId(), targetFeedId);
        if (feed != null && feed.stockQty() != null && feed.stockQty().compareTo(need) < 0) {
            throw stockNotEnough(feed.feedName(), feed.stockQty(), need);
        }
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        FeedRecord fresh = change.feedRecord(record.bizId());
        if (fresh == null) {
            throw notFound();
        }
        boolean voidApply = ChangeType.VOID.name().equals(request.getChangeType());
        if (voidApply) {
            if (!"NORMAL".equals(fresh.getStatus())) {
                throw new BizException(com.zhimu.server.common.error.ErrorCode.STATE_CHANGED, "这条投喂记录已作废");
            }
            fresh.setStatus("VOID");
            fresh.setVoidBy(adminId);
            fresh.setVoidAt(BeijingTime.now());
            fresh.setActiveTaskId(null);
            if (feedRecordMapper.updateById(fresh) == 0) {
                throw versionConflict();
            }
            if (fresh.getStockFlowId() != null) {
                feedStockPort.rollbackForFeeding(fresh.getStockFlowId());
            }
            restoreTask(fresh);
        } else {
            applyUpdate(fresh, request, adminId);
        }
        Map<String, Object> beforeValues = FeedingChangeSupport.displays(request.getBeforeJson(), "before");
        Map<String, Object> afterValues = voidApply
                ? Map.of("status", FeedingChangeSupport.value("记录状态", "已作废"))
                : FeedingChangeSupport.displays(request.getAfterJson(), "after");
        BatchInfo batch = change.batch(fresh.getBatchId());
        change.logApply(request, BizType.FEED_RECORD, fresh.getId(),
                change.penName(fresh.getPenId()), fresh.getFarmId(),
                batch == null ? null : batch.batchNo(), beforeValues, afterValues);
        return new ChangeApplyResult(voidApply ? 3 : 4,
                voidApply ? "已作废投喂记录并退回饲料库存" : "已重新计算投喂偏差与执行情况");
    }

    private void applyUpdate(FeedRecord fresh, ChangeRequest request, Long adminId) {
        String fieldCode = request.getFieldCode();
        String value = ChangeRequestJson.afterValue(request.getAfterJson(), fieldCode);
        Long targetFeedId = fresh.getFeedId();
        BigDecimal actual = fresh.getActualQty() == null ? BigDecimal.ZERO : fresh.getActualQty();
        if (FIELD_FEED_ID.equals(fieldCode)) {
            if (value == null) {
                throw FeedingChangeSupport.fieldError("new_value", "请选择饲料");
            }
            targetFeedId = Long.valueOf(value);
        } else if (FIELD_ACTUAL_QTY.equals(fieldCode)) {
            if (value == null) {
                throw FeedingChangeSupport.fieldError("new_value", "请填写实际投喂量");
            }
            actual = FeedingChangeSupport.decimalValue(value, "实际投喂量", MAX_QTY, UNIT);
        }
        Long oldFlow = fresh.getStockFlowId();
        fresh.setFeedId(targetFeedId);
        fresh.setActualQty(actual.setScale(2));
        BigDecimal threshold = fresh.getAbnormalThreshold() == null
                ? DEFAULT_THRESHOLD : fresh.getAbnormalThreshold();
        BigDecimal ratio = FeedCalc.deviation(actual, fresh.getPlanQty());
        fresh.setDeviationRatio(ratio);
        fresh.setAbnormalType(FeedCalc.abnormalType(ratio, threshold));
        if (feedRecordMapper.updateById(fresh) == 0) {
            throw versionConflict();
        }
        if (oldFlow != null) {
            feedStockPort.rollbackForFeeding(oldFlow);
        }
        FeedStockPort.DeductResult deduct = feedStockPort.deductForFeeding(new FeedStockPort.DeductCommand(
                fresh.getFarmId(), targetFeedId, actual.setScale(2), fresh.getBatchId(), fresh.getPenId(),
                fresh.getId(), fresh.getFeedDate(), adminId));
        if (deduct != null && deduct.insufficient()) {
            throw stockNotEnough(change.feedName(fresh.getFarmId(), targetFeedId), deduct.stockQty(), actual);
        }
        fresh.setStockFlowId(deduct == null ? null : deduct.stockFlowId());
        if (fresh.getStockFlowId() != null) {
            feedRecordMapper.updateById(fresh);
        }
    }

    /** 补登截止文案：任务 backfill_end_at 为次日 00:00，展示为“（10月19日 24:00 前）” */
    private String backfillDeadlineText(FeedRecord record) {
        FeedTask task = change.task(record.getTaskId());
        if (task == null || task.getBackfillEndAt() == null) {
            return "";
        }
        return "（" + change.monthDay(task.getBackfillEndAt().toLocalDate().minusDays(1)) + " 24:00 前）";
    }

    /** 作废后按补登期限恢复任务：超过补登截止记 MISSED，超过 deadline 记 OVERDUE，否则 PENDING */
    private void restoreTask(FeedRecord record) {        FeedTask task = change.task(record.getTaskId());
        if (task == null) {
            return;
        }
        LocalDateTime now = BeijingTime.now();
        String status;
        if (task.getBackfillEndAt() != null && !now.isBefore(task.getBackfillEndAt())) {
            status = FeedCalc.TASK_MISSED;
        } else if (task.getDeadlineAt() != null && now.isAfter(task.getDeadlineAt())) {
            status = FeedCalc.TASK_OVERDUE;
        } else {
            status = FeedCalc.TASK_PENDING;
        }
        task.setStatus(status);
        task.setRecordId(null);
        TenantSkip.run(() -> feedTaskMapper.updateById(task));
    }

    private BigDecimal needQty(FeedRecord fresh, ChangeRequest request) {
        if (FIELD_FEED_ID.equals(request.getFieldCode())) {
            return fresh.getActualQty() == null ? BigDecimal.ZERO : fresh.getActualQty();
        }
        String value = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_ACTUAL_QTY);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal after = new BigDecimal(value);
        BigDecimal before = fresh.getActualQty() == null ? BigDecimal.ZERO : fresh.getActualQty();
        BigDecimal delta = after.subtract(before);
        return delta.signum() > 0 ? delta : BigDecimal.ZERO;
    }

    private static Long targetFeedId(FeedRecord fresh, ChangeRequest request) {
        if (!FIELD_FEED_ID.equals(request.getFieldCode())) {
            return fresh.getFeedId();
        }
        String value = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_FEED_ID);
        return value == null || value.isBlank() ? fresh.getFeedId() : Long.valueOf(value);
    }

    private static BizException notFound() {
        return new BizException(com.zhimu.server.common.error.ErrorCode.NOT_FOUND, "这条记录不存在或已无权查看");
    }

    private static BizException stockNotEnough(String feedName, BigDecimal stockQty, BigDecimal needQty) {
        String message = "库存不足，不能确认投喂。本次需要 " + FeedingChangeSupport.plain(needQty) + " 千克，结存 "
                + FeedingChangeSupport.plain(stockQty) + " 千克，请先登记入库。";
        return new BizException(com.zhimu.server.common.error.ErrorCode.STOCK_NOT_ENOUGH, message)
                .with("stock_qty", stockQty)
                .with("need_qty", needQty);
    }

    private static BizException versionConflict() {
        return new BizException(com.zhimu.server.common.error.ErrorCode.VERSION_CONFLICT,
                "内容已更新，请刷新后重新提交");
    }
}
