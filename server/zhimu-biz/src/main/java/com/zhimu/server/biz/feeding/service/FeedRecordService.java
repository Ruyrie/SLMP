package com.zhimu.server.biz.feeding.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.feeding.entity.FeedPlan;
import com.zhimu.server.biz.feeding.entity.FeedRecord;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedPlanMapper;
import com.zhimu.server.biz.feeding.mapper.FeedRecordMapper;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.feeding.request.FeedRecordCreateRequest;
import com.zhimu.server.biz.feeding.request.FeedRecordUpdateRequest;
import com.zhimu.server.biz.feeding.request.FeedRecordVoidRequest;
import com.zhimu.server.biz.feeding.support.FeedCalc;
import com.zhimu.server.biz.feeding.support.FeedConvert;
import com.zhimu.server.biz.feeding.support.FeedErrors;
import com.zhimu.server.biz.feeding.support.FeedLookup;
import com.zhimu.server.biz.feeding.vo.FeedRecordVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** API-G04–G08 投喂登记、修改、作废与查询（技术准备文档 2.5.3、2.6.1、2.6.2）。 */
@Service
@RequiredArgsConstructor
public class FeedRecordService {

    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.1000");

    private final FeedRecordMapper recordMapper;
    private final FeedTaskMapper taskMapper;
    private final FeedPlanMapper planMapper;
    private final FeedTaskService taskService;
    private final FeedStockPort feedStockPort;
    private final FeedLookup lookup;
    private final TodoSourceWriteService todoWriter;

    /** G04 确认投喂 */
    @Transactional
    public FeedRecordVO.Detail create(FeedRecordCreateRequest request, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw FeedErrors.field("Idempotency-Key", "缺少幂等键");
        }
        FarmContext.FarmMember context = lookup.context();
        Long userId = UserContext.requireUserId();
        FeedRecord existing = recordMapper.selectOne(Wrappers.<FeedRecord>lambdaQuery()
                .eq(FeedRecord::getCreatedBy, userId)
                .eq(FeedRecord::getRequestId, requestId.trim())
                .last("LIMIT 1"));
        if (existing != null) {
            return detail(existing.getId());
        }
        if (request == null || request.getTaskId() == null) {
            throw FeedErrors.field("task_id", "请选择饲喂任务");
        }
        FeedTask task = taskService.requireTask(request.getTaskId());
        lookup.checkPenScope(task.getPenId());
        BatchInfo batch = lookup.requireOwnedBatch(task.getBatchId());
        LocalDateTime now = BeijingTime.now();
        String effective = FeedCalc.effectiveStatus(task, now);
        if (!batch.active()) {
            throw FeedErrors.stateChanged("批次已结束，不能再登记投喂");
        }
        if (FeedCalc.TASK_DONE.equals(effective)) {
            UserBriefVO registered = lookup.userBrief(task.getRecordId() == null ? null : recorderOf(task));
            throw FeedErrors.stateChanged(task.getMealName() + "已由"
                    + (registered == null || registered.getName() == null ? "他人" : registered.getName())
                    + "登记，不能重复登记");
        }
        if (FeedCalc.TASK_CANCELLED.equals(effective) || FeedCalc.TASK_IGNORED.equals(effective)) {
            throw FeedErrors.stateChanged("这条饲喂任务已失效");
        }
        if (FeedCalc.TASK_MISSED.equals(effective)
                || (task.getBackfillEndAt() != null && !now.isBefore(task.getBackfillEndAt()))) {
            throw FeedErrors.stateChanged("已超过补登期限，这餐已记为未执行");
        }
        if (!FeedCalc.TASK_PENDING.equals(effective) && !FeedCalc.TASK_OVERDUE.equals(effective)
                && !FeedCalc.TASK_MANUAL_DONE.equals(effective)) {
            throw FeedErrors.stateChanged("这条饲喂任务已失效");
        }
        FeedStockPort.FeedBrief feed = requireFeed(request.getFeedId());
        BigDecimal actualQty = validateQty(request.getActualQty());
        if (actualQty.compareTo(feed.stockQty()) > 0) {
            throw FeedErrors.stockNotEnough(feed.feedId(), feed.stockQty(), actualQty, context.isStaff());
        }
        Long executorId = resolveExecutor(request.getExecutorId(), context);
        String remark = validateRemark(request.getRemark());
        FeedPlan plan = plan(task.getBatchId());
        BigDecimal threshold = plan == null || plan.getAbnormalThreshold() == null
                ? DEFAULT_THRESHOLD : plan.getAbnormalThreshold();
        BigDecimal ratio = FeedCalc.deviation(actualQty, task.getPlanQty());
        String abnormal = FeedCalc.abnormalType(ratio, threshold);

        FeedRecord record = new FeedRecord();
        record.setFarmId(context.farmId());
        record.setBatchId(task.getBatchId());
        record.setPenId(task.getPenId());
        record.setTaskId(task.getId());
        record.setActiveTaskId(task.getId());
        record.setFeedDate(task.getTaskDate());
        record.setMealNo(task.getMealNo());
        record.setMealName(task.getMealName());
        record.setFeedId(feed.feedId());
        record.setPlanQty(task.getPlanQty());
        record.setActualQty(actualQty);
        record.setDeviationRatio(ratio);
        record.setAbnormalType(abnormal);
        record.setAbnormalThreshold(threshold);
        record.setExecutorId(executorId);
        record.setIsProxy(!userId.equals(executorId));
        record.setIsBackfill(task.getDeadlineAt() != null && now.isAfter(task.getDeadlineAt()));
        record.setRemark(remark);
        record.setStatus("NORMAL");
        record.setRequestId(requestId.trim());
        record.setVersion(0);
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw FeedErrors.stateChanged("这餐已被登记，不能重复登记");
        }
        FeedStockPort.DeductResult deduct = feedStockPort.deductForFeeding(new FeedStockPort.DeductCommand(
                context.farmId(), feed.feedId(), actualQty, task.getBatchId(), task.getPenId(), record.getId(),
                task.getTaskDate(), executorId));
        if (deduct != null && deduct.insufficient()) {
            throw FeedErrors.stockNotEnough(feed.feedId(), deduct.stockQty(), actualQty, context.isStaff());
        }
        if (deduct != null && deduct.stockFlowId() != null) {
            record.setStockFlowId(deduct.stockFlowId());
            recordMapper.updateById(record);
        }
        completeTask(task, record.getId());
        completeTodo(task, record, userId);
        FeedRecordVO.Detail detail = detail(record.getId());
        detail.setTodoCompleted(true);
        return detail;
    }

    /** G05 投喂记录详情 */
    public FeedRecordVO.Detail detail(Long recordId) {
        FeedRecord record = requireRecord(recordId);
        return toDetail(record);
    }

    /** G06 修改当天投喂记录 */
    @Transactional
    public FeedRecordVO.Detail update(Long recordId, FeedRecordUpdateRequest request) {
        lookup.requireManager("这条投喂已确认，不能再修改。需要更正时请联系所有者或场长。");
        FeedRecord record = requireRecord(recordId);
        if (!"NORMAL".equals(record.getStatus())) {
            throw FeedErrors.stateChanged("这条投喂记录已作废");
        }
        if (!BeijingTime.today().equals(record.getFeedDate())) {
            throw FeedErrors.changeRequired("这条记录不是今天登记的，修改需要提交修改申请，由管理员审核。");
        }
        if (request == null || request.getVersion() == null) {
            throw FeedErrors.field("version", "缺少版本号");
        }
        if (!request.getVersion().equals(record.getVersion())) {
            throw FeedErrors.versionConflict();
        }
        FeedStockPort.FeedBrief feed = requireFeed(request.getFeedId());
        BigDecimal actualQty = validateQty(request.getActualQty());
        BigDecimal delta = actualQty.subtract(record.getActualQty());
        if (!feed.feedId().equals(record.getFeedId()) && actualQty.compareTo(feed.stockQty()) > 0) {
            throw FeedErrors.stockNotEnough(feed.feedId(), feed.stockQty(), actualQty, false);
        }
        if (feed.feedId().equals(record.getFeedId()) && delta.signum() > 0
                && delta.compareTo(feed.stockQty()) > 0) {
            throw FeedErrors.stockNotEnough(feed.feedId(), feed.stockQty(), delta, false);
        }
        Long executorId = resolveExecutor(request.getExecutorId(), lookup.context());
        BigDecimal threshold = record.getAbnormalThreshold() == null ? DEFAULT_THRESHOLD : record.getAbnormalThreshold();
        BigDecimal ratio = FeedCalc.deviation(actualQty, record.getPlanQty());
        Long oldFlow = record.getStockFlowId();
        record.setFeedId(feed.feedId());
        record.setActualQty(actualQty);
        record.setDeviationRatio(ratio);
        record.setAbnormalType(FeedCalc.abnormalType(ratio, threshold));
        record.setExecutorId(executorId);
        record.setIsProxy(!UserContext.requireUserId().equals(executorId));
        record.setRemark(validateRemark(request.getRemark()));
        int updated = recordMapper.updateById(record);
        if (updated == 0) {
            throw FeedErrors.versionConflict();
        }
        if (oldFlow != null) {
            feedStockPort.rollbackForFeeding(record.getId());
        }
        FeedStockPort.DeductResult deduct = feedStockPort.deductForFeeding(new FeedStockPort.DeductCommand(
                record.getFarmId(), feed.feedId(), actualQty, record.getBatchId(), record.getPenId(), record.getId(),
                record.getFeedDate(), executorId));
        if (deduct != null && deduct.insufficient()) {
            throw FeedErrors.stockNotEnough(feed.feedId(), deduct.stockQty(), actualQty, false);
        }
        record.setStockFlowId(deduct == null ? null : deduct.stockFlowId());
        if (record.getStockFlowId() != null) {
            recordMapper.updateById(record);
        }
        return detail(record.getId());
    }

    /** G07 作废当天投喂记录 */
    @Transactional
    public FeedRecordVO.Detail voidRecord(Long recordId, FeedRecordVoidRequest request) {
        lookup.requireManager("这条投喂已确认，不能再修改。需要更正时请联系所有者或场长。");
        FeedRecord record = requireRecord(recordId);
        if (!"NORMAL".equals(record.getStatus())) {
            throw FeedErrors.stateChanged("这条投喂记录已作废");
        }
        if (!BeijingTime.today().equals(record.getFeedDate())) {
            throw FeedErrors.changeRequired("这条记录不是今天登记的，修改需要提交修改申请，由管理员审核。");
        }
        if (request == null || request.getVersion() == null) {
            throw FeedErrors.field("version", "缺少版本号");
        }
        if (!request.getVersion().equals(record.getVersion())) {
            throw FeedErrors.versionConflict();
        }
        record.setStatus("VOID");
        record.setVoidBy(UserContext.requireUserId());
        record.setVoidAt(BeijingTime.now());
        record.setActiveTaskId(null);
        int updated = recordMapper.updateById(record);
        if (updated == 0) {
            throw FeedErrors.versionConflict();
        }
        if (record.getStockFlowId() != null) {
            feedStockPort.rollbackForFeeding(record.getId());
        }
        restoreTask(record);
        return detail(record.getId());
    }

    /** G08 按幂等键查询当前用户登记的投喂 */
    public FeedRecordVO.Lookup lookup(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw FeedErrors.field("request_id", "请填写请求编号");
        }
        FeedRecord record = recordMapper.selectOne(Wrappers.<FeedRecord>lambdaQuery()
                .eq(FeedRecord::getCreatedBy, UserContext.requireUserId())
                .eq(FeedRecord::getRequestId, requestId.trim())
                .last("LIMIT 1"));
        FeedRecordVO.Lookup vo = new FeedRecordVO.Lookup();
        vo.setSaved(record != null);
        vo.setRecord(record == null ? null : detail(record.getId()));
        return vo;
    }

    private FeedRecord requireRecord(Long recordId) {
        if (recordId == null) {
            throw FeedErrors.notFound();
        }
        FeedRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw FeedErrors.notFound();
        }
        return record;
    }

    private FeedRecordVO.Detail toDetail(FeedRecord record) {
        FarmContext.FarmMember context = lookup.context();
        lookup.checkPenScope(record.getPenId());
        BatchInfo batch = lookup.requireOwnedBatch(record.getBatchId());
        FeedTask task = record.getTaskId() == null ? null : taskMapper.selectById(record.getTaskId());
        FeedPlan plan = plan(record.getBatchId());
        FeedStockPort.FeedBrief feed = lookup.feed(record.getFeedId());
        LocalDate today = BeijingTime.today();
        boolean todayRecord = today.equals(record.getFeedDate());
        boolean normal = "NORMAL".equals(record.getStatus());

        FeedRecordVO.Detail vo = new FeedRecordVO.Detail();
        vo.setRecordId(record.getId());
        vo.setTaskId(record.getTaskId());
        vo.setStatus(record.getStatus());
        vo.setStatusText(normal ? "正常" : "已作废");
        vo.setPenName(lookup.penName(record.getPenId()));
        vo.setMealName(record.getMealName());
        vo.setFeedTime(task == null ? null : formatTime(task.getFeedTime()));
        vo.setFeedDate(BeijingTime.format(record.getFeedDate()));
        vo.setBatchId(record.getBatchId());
        vo.setBatchNo(batch.batchNo());
        vo.setBreedName(lookup.breedName(batch.breedId()));
        vo.setDayAge(FeedCalc.dayAge(batch, record.getFeedDate()));
        vo.setBatchStock(batch.currentCount());
        vo.setFeed(FeedConvert.option(feed));
        vo.setPlanQty(record.getPlanQty());
        if (task != null) {
            FeedRecordVO.PlanFormula formula = new FeedRecordVO.PlanFormula();
            formula.setHeadCount(task.getHeadCount());
            formula.setDailyQtyPerHead(task.getDailyQtyPerHead());
            formula.setMealsPerDay(task.getMealsPerDay());
            vo.setPlanFormula(formula);
        }
        vo.setActualQty(record.getActualQty());
        vo.setDeviationRatio(record.getDeviationRatio());
        vo.setAbnormalType(record.getAbnormalType());
        vo.setAbnormalTypeText(FeedCalc.abnormalTypeText(record.getAbnormalType()));
        vo.setAbnormalThreshold(record.getAbnormalThreshold());
        UserBriefVO executor = lookup.userBrief(record.getExecutorId());
        UserBriefVO recorder = lookup.userBrief(record.getCreatedBy());
        vo.setExecutor(executor);
        vo.setRecorder(recorder);
        vo.setIsProxy(record.getIsProxy());
        vo.setProxyExecutorName(Boolean.TRUE.equals(record.getIsProxy()) && executor != null
                ? executor.getName() : null);
        vo.setIsBackfill(record.getIsBackfill());
        vo.setRegisteredAt(BeijingTime.format(record.getCreatedAt()));
        vo.setRemark(record.getRemark());
        String viewMode;
        String readonlyReason = null;
        if (!normal) {
            viewMode = "READONLY";
            readonlyReason = "VOIDED";
        } else if (context.isStaff()) {
            viewMode = "READONLY";
            readonlyReason = "STAFF_CONFIRMED";
        } else if (!todayRecord) {
            viewMode = "READONLY";
            readonlyReason = "NOT_TODAY";
        } else {
            viewMode = "EDIT";
        }
        vo.setViewMode(viewMode);
        vo.setReadonlyReason(readonlyReason);
        vo.setChangeState(ChangeRequestStateVO.builder()
                .canEdit(normal && todayRecord && context.isManagerView())
                .editDeadline(record.getFeedDate() == null ? null : record.getFeedDate().plusDays(1).atStartOfDay())
                .pendingRequestId(null)
                .build());
        vo.setCanVoid(normal && todayRecord && context.isManagerView());
        vo.setCanRequestChange(normal && !todayRecord);
        vo.setChangeFields(List.of("feed_id", "actual_qty"));
        vo.setRecalcTags(List.of("头均采食量", "料肉比", "饲料成本", "经营报表"));
        vo.setVersion(record.getVersion() == null ? 0 : record.getVersion());
        return vo;
    }

    private FeedPlan plan(Long batchId) {
        return planMapper.selectOne(Wrappers.<FeedPlan>lambdaQuery()
                .eq(FeedPlan::getBatchId, batchId).last("LIMIT 1"));
    }

    private FeedStockPort.FeedBrief requireFeed(Long feedId) {
        if (feedId == null) {
            throw FeedErrors.field("feed_id", "请选择饲料");
        }
        FeedStockPort.FeedBrief feed = lookup.feed(feedId);
        if (feed == null) {
            throw FeedErrors.field("feed_id", "请选择饲料");
        }
        if (!feed.selectable()) {
            throw FeedErrors.field("feed_id", feed.disabledReason() == null ? "该饲料不可出库" : feed.disabledReason());
        }
        return feed;
    }

    private BigDecimal validateQty(BigDecimal qty) {
        if (qty == null) {
            throw FeedErrors.field("actual_qty", "请填写实际投喂量");
        }
        if (qty.signum() <= 0) {
            throw FeedErrors.field("actual_qty", "实际投喂量要大于 0 千克");
        }
        if (qty.scale() > 1) {
            throw FeedErrors.field("actual_qty", "实际投喂量最多 1 位小数");
        }
        if (qty.compareTo(new BigDecimal("99999.9")) > 0) {
            throw FeedErrors.field("actual_qty", "实际投喂量不能超过 99999.9 千克");
        }
        return qty.setScale(2);
    }

    private String validateRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String text = remark.trim();
        if (text.length() > 100) {
            throw FeedErrors.field("remark", "备注不能超过 100 字");
        }
        return text.isEmpty() ? null : text;
    }

    private Long resolveExecutor(Long executorId, FarmContext.FarmMember context) {
        if (context.isStaff()) {
            return UserContext.requireUserId();
        }
        if (executorId == null) {
            throw FeedErrors.field("executor_id", "请选择执行人");
        }
        if (!lookup.isActiveMember(executorId)) {
            throw FeedErrors.field("executor_id", "执行人不是本场在职成员");
        }
        return executorId;
    }

    private Long recorderOf(FeedTask task) {
        FeedRecord record = recordMapper.selectById(task.getRecordId());
        return record == null ? null : record.getCreatedBy();
    }

    /** 登记投喂后自动完成对应待办（幂等；待办不存在或已非 PENDING 时无操作） */
    private void completeTodo(FeedTask task, FeedRecord record, Long operatorId) {
        todoWriter.completeFromSource(record.getFarmId(), FeedTodoSource.TODO_TYPE,
                FeedTodoSource.dedupKey(task.getId()), "FEED_RECORD", record.getId(), operatorId);
    }

    private void completeTask(FeedTask task, Long recordId) {
        task.setStatus(FeedCalc.TASK_DONE);
        task.setRecordId(recordId);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw FeedErrors.stateChanged(task.getMealName() + "已被他人登记，请刷新后重试");
        }
    }

    private void restoreTask(FeedRecord record) {
        FeedTask task = taskMapper.selectById(record.getTaskId());
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
        taskMapper.updateById(task);
        if (FeedCalc.TASK_MISSED.equals(status)) {
            todoWriter.ignoreFromSource(record.getFarmId(), FeedTodoSource.TODO_TYPE,
                    FeedTodoSource.dedupKey(task.getId()), FeedCalc.CLOSE_REASON_BACKFILL_EXPIRED);
        } else {
            todoWriter.reopenFromSource(record.getFarmId(), FeedTodoSource.TODO_TYPE,
                    FeedTodoSource.dedupKey(task.getId()), record.getId());
        }
    }

    private static String formatTime(java.time.LocalTime time) {
        return time == null ? null : time.format(BeijingTime.TIME);
    }
}
