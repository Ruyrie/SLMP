package com.zhimu.server.biz.feeding.support.change;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import com.zhimu.server.biz.feeding.entity.BatchGrowth;
import com.zhimu.server.biz.feeding.entity.WeighRecord;
import com.zhimu.server.biz.feeding.entity.WeighRecordItem;
import com.zhimu.server.biz.feeding.mapper.BatchGrowthMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordItemMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordMapper;
import com.zhimu.server.biz.feeding.support.GrowthCalc;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 称重记录（WEIGH_RECORD，biz_id = 称重记录 ID，record_version = 称重记录 version）。
 * <p>
 * 字段与 App Mock（change_form_weigh_record.json）一致：avg_weight 平均体重（千克，0–500，辅助文字“标准 52.3 千克”）。
 * 批次已结束的记录走修改申请；审核通过重算总重、偏离与生长结果、本条与后一条日增重，并刷新批次生长汇总。
 */
@Component
@RequiredArgsConstructor
public class WeighRecordChangeHandler implements ChangeRequestHandler {

    private static final String FIELD_AVG_WEIGHT = "avg_weight";
    private static final String UNIT = "千克";
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("500");

    private final FeedingChangeSupport change;
    private final WeighRecordMapper weighRecordMapper;
    private final WeighRecordItemMapper itemMapper;
    private final BatchGrowthMapper batchGrowthMapper;

    @Override
    public Set<BizType> supportedBizTypes() {
        return Set.of(BizType.WEIGH_RECORD);
    }

    @Override
    public SourceModule sourceModule() {
        return ChangeBizTypes.sourceModuleOf(BizType.WEIGH_RECORD);
    }

    @Override
    public ChangeRecord loadRecord(BizType bizType, Long bizId) {
        WeighRecord record = change.weighRecord(bizId);
        if (record == null) {
            return null;
        }
        BatchInfo batch = change.batch(record.getBatchId());
        Long penId = batch == null ? null : batch.entryPenId();
        String penName = change.penName(penId);
        String batchNo = batch == null ? "" : batch.batchNo();
        String date = change.monthDay(record.getWeighDate());
        String locator = batchNo + " · " + date + "称重";
        String title = "称重记录 · " + penName;
        String subtitle = batchNo + " · " + date + " · " + change.userName(record.getCreatedBy()) + "登记";
        int version = record.getVersion() == null ? 0 : record.getVersion();
        return new ChangeRecord(BizType.WEIGH_RECORD, record.getId(), record.getFarmId(), record.getBatchId(),
                penId, locator, title, subtitle, String.valueOf(version), false, false);
    }

    @Override
    public boolean requiresRequest(ChangeRecord record) {
        WeighRecord fresh = change.weighRecord(record.bizId());
        if (fresh == null) {
            return true;
        }
        BatchInfo batch = change.batch(fresh.getBatchId());
        return batch == null || !batch.active();
    }

    @Override
    public ChangeForm describeForm(ChangeRecord record) {
        WeighRecord fresh = change.weighRecord(record.bizId());
        if (fresh == null) {
            throw notFound();
        }
        BigDecimal avg = fresh.getAvgWeight() == null ? BigDecimal.ZERO : fresh.getAvgWeight();
        int sample = fresh.getSampleCount() == null ? 0 : fresh.getSampleCount();
        String hint = fresh.getStdWeight() == null ? null
                : "标准 " + FeedingChangeSupport.plain(fresh.getStdWeight()) + " " + UNIT;
        ChangeField field = new ChangeField(FIELD_AVG_WEIGHT, "平均体重",
                FeedingChangeSupport.plain(avg), UNIT, ChangeField.NUMBER, List.of(), BigDecimal.ZERO, MAX_WEIGHT,
                List.of("日增重", "生长曲线"), hint);
        return new ChangeForm(
                List.of(new ChangeCardItem("抽样头数", sample + " 头"),
                        new ChangeCardItem("平均体重", FeedingChangeSupport.display(
                                FeedingChangeSupport.plain(avg), UNIT))),
                List.of(field),
                List.of("修改平均体重会重新计算日增重与生长曲线偏离"),
                "这条记录会影响生长曲线和出栏预估，需要管理员审核。审核期间原记录照常有效，审核通过后才会修改并重新计算。",
                false, false, null, List.of());
    }

    @Override
    public ChangeValidation validateChange(ChangeRecord record, ChangeForm form, ChangeType changeType,
                                           String fieldCode, String newValue) {
        if (changeType != ChangeType.UPDATE) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "这条称重记录暂不能作废或删除");
        }
        FeedingChangeSupport.requireField(form, fieldCode);
        if (!FIELD_AVG_WEIGHT.equals(fieldCode)) {
            throw FeedingChangeSupport.fieldError("field_code", "请选择要修改的项目");
        }
        WeighRecord fresh = change.weighRecord(record.bizId());
        if (fresh == null) {
            throw notFound();
        }
        BigDecimal before = fresh.getAvgWeight() == null ? BigDecimal.ZERO : fresh.getAvgWeight();
        BigDecimal after = FeedingChangeSupport.decimalValue(newValue, "平均体重", MAX_WEIGHT, UNIT);
        if (after.compareTo(before) == 0) {
            FeedingChangeSupport.sameValue();
        }
        return ChangeValidation.of(FIELD_AVG_WEIGHT, "平均体重", UNIT, FeedingChangeSupport.plain(before),
                FeedingChangeSupport.display(FeedingChangeSupport.plain(before), UNIT),
                FeedingChangeSupport.plain(after), FeedingChangeSupport.display(FeedingChangeSupport.plain(after), UNIT),
                List.of(ChangeImpact.STATS), List.of("日增重", "生长曲线"));
    }

    @Override
    public void validateForApprove(ChangeRequest request, ChangeRecord record) {
        WeighRecord fresh = change.weighRecord(record.bizId());
        if (fresh == null) {
            throw new BizException(ErrorCode.STATE_CHANGED, "原称重记录已不存在，请驳回");
        }
        if (fresh.getSampleCount() == null || fresh.getSampleCount() < 1) {
            throw new BizException(ErrorCode.STATE_CHANGED, "原称重记录缺少抽样头数，请驳回");
        }
    }

    @Override
    public ChangeApplyResult apply(ChangeRequest request, ChangeRecord record, Long adminId) {
        WeighRecord fresh = change.weighRecord(record.bizId());
        if (fresh == null) {
            throw notFound();
        }
        String value = ChangeRequestJson.afterValue(request.getAfterJson(), FIELD_AVG_WEIGHT);
        if (value == null) {
            throw FeedingChangeSupport.fieldError("new_value", "请填写平均体重");
        }
        BigDecimal avg = FeedingChangeSupport.decimalValue(value, "平均体重", MAX_WEIGHT, UNIT);
        int sample = fresh.getSampleCount() == null || fresh.getSampleCount() < 1 ? 1 : fresh.getSampleCount();
        boolean individual = "INDIVIDUAL".equals(fresh.getMode());
        boolean latest = isLatest(fresh);
        fresh.setMode(individual ? "SAMPLE" : fresh.getMode());
        fresh.setAvgWeight(avg.setScale(2, RoundingMode.HALF_UP));
        fresh.setTotalWeight(avg.multiply(BigDecimal.valueOf(sample)).setScale(2, RoundingMode.HALF_UP));
        fresh.setSampleLow(fresh.getSuggestedSample() != null && sample < fresh.getSuggestedSample());
        fresh.setDeviationRatio(GrowthCalc.deviation(fresh.getAvgWeight(), fresh.getStdWeight()));
        fresh.setGrowthResult(GrowthCalc.growthResult(fresh.getDeviationRatio(), fresh.getAllowedDeviation()));
        fresh.setDailyGain(dailyGain(fresh));
        fresh.setUpdatedBy(adminId);
        if (weighRecordMapper.updateById(fresh) == 0) {
            throw versionConflict();
        }
        if (individual) {
            TenantSkip.run(() -> itemMapper.delete(Wrappers.<WeighRecordItem>lambdaQuery()
                    .eq(WeighRecordItem::getWeighRecordId, fresh.getId())));
        }
        recalcNext(fresh);
        updateGrowth(fresh, latest);
        Map<String, Object> beforeValues = FeedingChangeSupport.displays(request.getBeforeJson(), "before");
        Map<String, Object> afterValues = FeedingChangeSupport.displays(request.getAfterJson(), "after");
        BatchInfo batch = change.batch(fresh.getBatchId());
        change.logApply(request, BizType.WEIGH_RECORD, fresh.getId(),
                batch == null ? null : batch.batchNo(), fresh.getFarmId(),
                batch == null ? null : batch.batchNo(), beforeValues, afterValues);
        return new ChangeApplyResult(4, "已重新计算日增重与生长曲线");
    }

    /** 本条日增重：有基准体重时按（新均重 − 基准）÷ 天数重算 */
    private static BigDecimal dailyGain(WeighRecord record) {
        if (record.getGainBaseDate() == null || record.getGainBaseWeight() == null || record.getWeighDate() == null) {
            return record.getDailyGain();
        }
        long days = ChronoUnit.DAYS.between(record.getGainBaseDate(), record.getWeighDate());
        return GrowthCalc.dailyGain(record.getAvgWeight(), record.getGainBaseWeight(), days);
    }

    /** 后一条称重记录以本条为日增重基准，需随之重算 */
    private void recalcNext(WeighRecord record) {
        List<WeighRecord> rows = TenantSkip.call(() -> weighRecordMapper.selectList(
                Wrappers.<WeighRecord>lambdaQuery()
                        .eq(WeighRecord::getBatchId, record.getBatchId())
                        .gt(WeighRecord::getWeighDate, record.getWeighDate())
                        .orderByAsc(WeighRecord::getWeighDate)
                        .last("LIMIT 1")));
        if (rows.isEmpty()) {
            return;
        }
        WeighRecord next = rows.get(0);
        long days = ChronoUnit.DAYS.between(record.getWeighDate(), next.getWeighDate());
        next.setGainBaseType("PREV_WEIGH");
        next.setGainBaseDate(record.getWeighDate());
        next.setGainBaseWeight(record.getAvgWeight());
        next.setDailyGain(GrowthCalc.dailyGain(next.getAvgWeight(), record.getAvgWeight(), days));
        if (next.getStdWeight() != null) {
            next.setDeviationRatio(GrowthCalc.deviation(next.getAvgWeight(), next.getStdWeight()));
            next.setGrowthResult(GrowthCalc.growthResult(next.getDeviationRatio(), next.getAllowedDeviation()));
        }
        TenantSkip.run(() -> weighRecordMapper.updateById(next));
    }

    private boolean isLatest(WeighRecord record) {
        List<WeighRecord> rows = TenantSkip.call(() -> weighRecordMapper.selectList(
                Wrappers.<WeighRecord>lambdaQuery()
                        .eq(WeighRecord::getBatchId, record.getBatchId())
                        .gt(WeighRecord::getWeighDate, record.getWeighDate())
                        .last("LIMIT 1")));
        return rows.isEmpty();
    }

    /** 本条为最新称重时刷新 t_batch_growth 的 last_* 与生长标记，并重算下次称重日期 */
    private void updateGrowth(WeighRecord record, boolean latest) {
        if (!latest) {
            return;
        }
        BatchGrowth growth = change.growth(record.getBatchId());
        boolean created = false;
        if (growth == null) {
            growth = new BatchGrowth();
            growth.setFarmId(record.getFarmId());
            growth.setBatchId(record.getBatchId());
            growth.setWeighCyclePerMonth(2);
            growth.setCycleCustomized(false);
            growth.setGrowthStatus("NONE");
            growth.setVersion(0);
            created = true;
        }
        String before = growth.getGrowthStatus();
        growth.setLastWeighId(record.getId());
        growth.setLastWeighDate(record.getWeighDate());
        growth.setLastAvgWeight(record.getAvgWeight());
        growth.setLastDeviation(record.getDeviationRatio());
        growth.setLastDailyGain(record.getDailyGain());
        growth.setGrowthStatus(record.getGrowthResult());
        if (before == null ? record.getGrowthResult() != null : !before.equals(record.getGrowthResult())) {
            growth.setGrowthStatusAt(BeijingTime.now());
        }
        int perMonth = growth.getWeighCyclePerMonth() == null ? 2 : growth.getWeighCyclePerMonth();
        LocalDate next = record.getWeighDate() == null ? null
                : record.getWeighDate().plusDays(GrowthCalc.intervalDays(perMonth));
        LocalDate today = BeijingTime.today();
        if (next != null && next.isBefore(today)) {
            next = today;
        }
        growth.setNextWeighDate(next);
        BatchGrowth row = growth;
        boolean insert = created;
        TenantSkip.run(() -> {
            if (insert) {
                batchGrowthMapper.insert(row);
            } else {
                batchGrowthMapper.updateById(row);
            }
        });
    }

    private static BizException notFound() {
        return new BizException(ErrorCode.NOT_FOUND, "这条记录不存在或已无权查看");
    }

    private static BizException versionConflict() {
        return new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后重新提交");
    }

    /** 测试与留痕用的前后值（保留字段名口径） */
    static Map<String, Object> values(BigDecimal before, BigDecimal after) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(FIELD_AVG_WEIGHT, FeedingChangeSupport.value("平均体重",
                FeedingChangeSupport.display(FeedingChangeSupport.plain(before), UNIT) + " → "
                        + FeedingChangeSupport.display(FeedingChangeSupport.plain(after), UNIT)));
        return map;
    }
}
