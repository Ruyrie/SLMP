package com.zhimu.server.biz.feeding.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.feeding.entity.FeedPlan;
import com.zhimu.server.biz.feeding.entity.FeedRecord;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedPlanMapper;
import com.zhimu.server.biz.feeding.mapper.FeedRecordMapper;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.feeding.request.FeedExecQuery;
import com.zhimu.server.biz.feeding.support.FeedCalc;
import com.zhimu.server.biz.feeding.support.FeedErrors;
import com.zhimu.server.biz.feeding.support.FeedLookup;
import com.zhimu.server.biz.feeding.vo.FeedExecVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** API-G14–G16 执行情况（技术准备文档 2.5.5、2.7.5）。 */
@Service
@RequiredArgsConstructor
public class FeedExecutionService {

    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.1000");

    private final FeedTaskMapper taskMapper;
    private final FeedRecordMapper recordMapper;
    private final FeedPlanMapper planMapper;
    private final FeedLookup lookup;

    /** G14 执行情况汇总 */
    public FeedExecVO.Summary summary(FeedExecQuery query) {
        lookup.requireManager("你没有查看饲喂执行情况的权限");
        Range range = resolve(query);
        List<FeedTask> tasks = tasks(range);
        List<FeedRecord> records = records(range);
        Map<Long, FeedRecord> recordByTask = records.stream()
                .collect(Collectors.toMap(FeedRecord::getTaskId, r -> r, (a, b) -> a));
        FeedExecVO.Summary vo = new FeedExecVO.Summary();
        vo.setStartDate(BeijingTime.format(range.start()));
        vo.setEndDate(BeijingTime.format(range.end()));
        vo.setRange(range.range());
        vo.setBatchCount(range.batchIds().size());
        vo.setHasData(!range.batchIds().isEmpty() && !tasks.isEmpty());
        vo.setEmptyReason(range.batchIds().isEmpty() ? "NO_ACTIVE_BATCH" : (tasks.isEmpty() ? "NO_TASK" : null));

        int planned = tasks.size();
        int done = 0;
        BigDecimal planQty = BigDecimal.ZERO;
        BigDecimal actualQty = BigDecimal.ZERO;
        BigDecimal doneActual = BigDecimal.ZERO;
        BigDecimal donePlan = BigDecimal.ZERO;
        int under = 0;
        int over = 0;
        BigDecimal sameRatio = null;
        boolean same = true;
        for (FeedTask task : tasks) {
            planQty = planQty.add(nz(task.getPlanQty()));
            if (FeedCalc.TASK_DONE.equals(task.getStatus()) || FeedCalc.TASK_MANUAL_DONE.equals(task.getStatus())) {
                done++;
                FeedRecord record = recordByTask.get(task.getId());
                if (record != null) {
                    doneActual = doneActual.add(nz(record.getActualQty()));
                    donePlan = donePlan.add(nz(task.getPlanQty()));
                }
            }
        }
        for (FeedRecord record : records) {
            actualQty = actualQty.add(nz(record.getActualQty()));
            if (record.getAbnormalType() != null && !"NONE".equals(record.getAbnormalType())) {
                if ("UNDER".equals(record.getAbnormalType())) {
                    under++;
                } else if ("OVER".equals(record.getAbnormalType())) {
                    over++;
                }
                if (sameRatio == null) {
                    sameRatio = record.getDeviationRatio();
                } else if (sameRatio.compareTo(record.getDeviationRatio()) != 0) {
                    same = false;
                }
            }
        }
        vo.setPlannedMeals(planned);
        vo.setDoneMeals(done);
        vo.setExecRate(planned == 0 ? zero4() : BigDecimal.valueOf(done)
                .divide(BigDecimal.valueOf(planned), 4, RoundingMode.HALF_UP));
        BigDecimal deviation = donePlan.signum() == 0 ? zero4()
                : doneActual.subtract(donePlan).divide(donePlan, 4, RoundingMode.HALF_UP);
        vo.setDeviationRatio(deviation);
        vo.setDeviationDirection(deviation.signum() < 0 ? "LESS" : (deviation.signum() > 0 ? "MORE" : "EQUAL"));
        vo.setActualQty(FeedCalc.round2(actualQty));
        vo.setPlanQty(FeedCalc.round2(planQty));
        vo.setAbnormalCount(under + over);
        vo.setUnderCount(under);
        vo.setOverCount(over);
        // 同比例偏差：所有异常餐次的偏差比例一致时给出该比例的绝对值（App 显示“同比例偏差 12.5%”）
        vo.setAbnormalSameRatio(same && sameRatio != null ? sameRatio.abs() : null);
        vo.setPlanLineType(lineType(range, tasks));
        vo.setPlanDailyQty(singlePlanDaily(range, tasks));
        vo.setDaily(daily(range, tasks, records));
        return vo;
    }

    /** G15 需要关注 */
    public PageVO<FeedExecVO.AttentionItem> attention(FeedExecQuery query, int page, int size) {
        lookup.requireManager("你没有查看饲喂执行情况的权限");
        Range range = resolve(query);
        List<FeedTask> tasks = tasks(range);
        List<FeedRecord> records = records(range);
        LocalDateTime now = BeijingTime.now();
        LocalDate today = BeijingTime.today();
        Map<Long, FeedTask> taskById = tasks.stream().collect(Collectors.toMap(FeedTask::getId, t -> t));
        Set<Long> batchIds = tasks.stream().map(FeedTask::getBatchId).collect(Collectors.toSet());
        Map<Long, BatchInfo> batches = new HashMap<>();
        for (Long batchId : batchIds) {
            try {
                batches.put(batchId, lookup.requireOwnedBatch(batchId));
            } catch (RuntimeException ignored) {
                // 批次不属于本场时忽略
            }
        }
        Set<Long> penIds = tasks.stream().map(FeedTask::getPenId).collect(Collectors.toSet());
        Map<Long, PenInfo> pens = lookup.pens(penIds);
        Set<Long> userIds = new LinkedHashSet<>();
        for (FeedTask task : tasks) {
            if (FeedCalc.TASK_OVERDUE.equals(FeedCalc.effectiveStatus(task, now))
                    || FeedCalc.TASK_MISSED.equals(task.getStatus())) {
                userIds.add(task.getAssigneeId());
            }
        }
        for (FeedRecord record : records) {
            userIds.add(record.getExecutorId());
        }
        userIds.remove(null);
        Map<Long, UserBriefVO> members = lookup.memberBriefs(userIds);

        List<FeedExecVO.AttentionItem> items = new ArrayList<>();
        Set<Long> abnormalTaskIds = new LinkedHashSet<>();
        for (FeedRecord record : records) {
            if (record.getAbnormalType() == null || "NONE".equals(record.getAbnormalType())) {
                continue;
            }
            FeedTask task = taskById.get(record.getTaskId());
            BatchInfo batch = batches.get(record.getBatchId());
            FeedExecVO.AttentionItem item = new FeedExecVO.AttentionItem();
            item.setType("ABNORMAL");
            item.setDate(BeijingTime.format(record.getFeedDate()));
            item.setMealName(record.getMealName());
            item.setBatchId(record.getBatchId());
            item.setBatchNo(batch == null ? null : batch.batchNo());
            item.setPenName(penName(pens, record.getPenId()));
            item.setPlanQty(record.getPlanQty());
            item.setActualQty(record.getActualQty());
            UserBriefVO executor = members.get(record.getExecutorId());
            item.setExecutorName(executor == null ? null : executor.getName());
            item.setAbnormalType(record.getAbnormalType());
            item.setDeviationRatio(record.getDeviationRatio());
            item.setTaskStatus(task == null ? null : FeedCalc.effectiveStatus(task, now));
            item.setTaskStatusText(FeedCalc.statusText(item.getTaskStatus()));
            item.setTaskId(record.getTaskId());
            item.setRecordId(record.getId());
            item.setCanBackfill(false);
            item.setIsToday(today.equals(record.getFeedDate()));
            items.add(item);
            abnormalTaskIds.add(record.getTaskId());
        }
        for (FeedTask task : tasks) {
            String effective = FeedCalc.effectiveStatus(task, now);
            boolean missed = FeedCalc.TASK_MISSED.equals(task.getStatus());
            boolean overdue = FeedCalc.TASK_OVERDUE.equals(effective);
            if (!missed && !overdue) {
                continue;
            }
            if (abnormalTaskIds.contains(task.getId())) {
                continue;
            }
            BatchInfo batch = batches.get(task.getBatchId());
            FeedExecVO.AttentionItem item = new FeedExecVO.AttentionItem();
            item.setType("OVERDUE");
            item.setDate(BeijingTime.format(task.getTaskDate()));
            item.setMealName(task.getMealName());
            item.setBatchId(task.getBatchId());
            item.setBatchNo(batch == null ? null : batch.batchNo());
            item.setPenName(penName(pens, task.getPenId()));
            item.setPlanQty(task.getPlanQty());
            item.setActualQty(null);
            UserBriefVO assignee = members.get(task.getAssigneeId());
            item.setExecutorName(assignee == null ? null : assignee.getName());
            item.setAbnormalType(null);
            item.setDeviationRatio(null);
            item.setTaskStatus(missed ? FeedCalc.TASK_MISSED : FeedCalc.TASK_OVERDUE);
            item.setTaskStatusText(FeedCalc.statusText(item.getTaskStatus()));
            item.setTaskId(task.getId());
            item.setRecordId(null);
            item.setCanBackfill(task.getBackfillEndAt() != null && now.isBefore(task.getBackfillEndAt()));
            item.setIsToday(today.equals(task.getTaskDate()));
            items.add(item);
        }
        items.sort(Comparator.comparing(FeedExecVO.AttentionItem::getDate,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FeedExecVO.AttentionItem::getMealName,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        int from = Math.min((page - 1) * size, items.size());
        int to = Math.min(from + size, items.size());
        return new PageVO<>(new ArrayList<>(items.subList(from, to)), items.size(), page, size);
    }

    /** G16 单日各餐明细 */
    public FeedExecVO.Day day(FeedExecQuery query) {
        lookup.requireManager("你没有查看饲喂执行情况的权限");
        Range range = resolve(query);
        LocalDate date = query.getDate();
        if (date == null) {
            throw FeedErrors.field("date", "请选择要查看的日期");
        }
        if (date.isBefore(range.start()) || date.isAfter(range.end())) {
            throw FeedErrors.field("date", "日期不在统计范围内");
        }
        FeedExecVO.Day vo = new FeedExecVO.Day();
        vo.setDate(BeijingTime.format(date));
        List<FeedTask> tasks = taskMapper.selectList(Wrappers.<FeedTask>lambdaQuery()
                .in(FeedTask::getBatchId, range.batchIds())
                .eq(FeedTask::getTaskDate, date)
                .notIn(FeedTask::getStatus, List.of(FeedCalc.TASK_CANCELLED, FeedCalc.TASK_IGNORED))
                .orderByAsc(FeedTask::getMealNo)
                .orderByAsc(FeedTask::getPenId));
        List<FeedRecord> records = recordMapper.selectList(Wrappers.<FeedRecord>lambdaQuery()
                .in(FeedRecord::getBatchId, range.batchIds())
                .eq(FeedRecord::getFeedDate, date)
                .eq(FeedRecord::getStatus, "NORMAL"));
        Map<Long, FeedRecord> recordByTask = records.stream()
                .collect(Collectors.toMap(FeedRecord::getTaskId, r -> r, (a, b) -> a));
        Map<Long, BatchInfo> batches = new HashMap<>();
        for (FeedTask task : tasks) {
            batches.computeIfAbsent(task.getBatchId(), id -> lookup.requireOwnedBatch(id));
        }
        Set<Long> penIds = tasks.stream().map(FeedTask::getPenId).collect(Collectors.toSet());
        Map<Long, PenInfo> pens = lookup.pens(penIds);
        Set<Long> executorIds = records.stream().map(FeedRecord::getExecutorId).collect(Collectors.toSet());
        Map<Long, UserBriefVO> members = lookup.memberBriefs(executorIds);
        LocalDateTime now = BeijingTime.now();
        BigDecimal plan = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        int done = 0;
        for (FeedTask task : tasks) {
            FeedRecord record = recordByTask.get(task.getId());
            FeedExecVO.DayItem item = new FeedExecVO.DayItem();
            item.setTaskId(task.getId());
            BatchInfo batch = batches.get(task.getBatchId());
            item.setBatchNo(batch == null ? null : batch.batchNo());
            item.setPenName(penName(pens, task.getPenId()));
            item.setMealName(task.getMealName());
            item.setPlanQty(task.getPlanQty());
            String effective = FeedCalc.effectiveStatus(task, now);
            if (record != null) {
                item.setActualQty(record.getActualQty());
                item.setAbnormalType(record.getAbnormalType());
                UserBriefVO executor = members.get(record.getExecutorId());
                item.setExecutorName(executor == null ? null : executor.getName());
                actual = actual.add(nz(record.getActualQty()));
            }
            item.setStatus(effective);
            item.setStatusText(FeedCalc.statusText(effective));
            if (FeedCalc.TASK_DONE.equals(effective) || FeedCalc.TASK_MANUAL_DONE.equals(effective)) {
                done++;
            }
            plan = plan.add(nz(task.getPlanQty()));
            vo.getItems().add(item);
        }
        vo.setPlanQty(FeedCalc.round2(plan));
        vo.setActualQty(FeedCalc.round2(actual));
        vo.setPlannedMeals(tasks.size());
        vo.setDoneMeals(done);
        return vo;
    }

    private List<FeedExecVO.Daily> daily(Range range, List<FeedTask> tasks, List<FeedRecord> records) {
        Map<LocalDate, List<FeedTask>> taskByDate = tasks.stream()
                .collect(Collectors.groupingBy(FeedTask::getTaskDate));
        Map<LocalDate, List<FeedRecord>> recordByDate = records.stream()
                .collect(Collectors.groupingBy(FeedRecord::getFeedDate));
        BigDecimal threshold = range.threshold() == null ? DEFAULT_THRESHOLD : range.threshold();
        List<FeedExecVO.Daily> daily = new ArrayList<>();
        for (LocalDate date = range.start(); !date.isAfter(range.end()); date = date.plusDays(1)) {
            List<FeedTask> dayTasks = taskByDate.getOrDefault(date, List.of());
            List<FeedRecord> dayRecords = recordByDate.getOrDefault(date, List.of());
            BigDecimal plan = BigDecimal.ZERO;
            BigDecimal actual = BigDecimal.ZERO;
            int done = 0;
            for (FeedTask task : dayTasks) {
                plan = plan.add(nz(task.getPlanQty()));
                if (FeedCalc.TASK_DONE.equals(task.getStatus()) || FeedCalc.TASK_MANUAL_DONE.equals(task.getStatus())) {
                    done++;
                }
            }
            for (FeedRecord record : dayRecords) {
                actual = actual.add(nz(record.getActualQty()));
            }
            BigDecimal ratio = plan.signum() == 0 ? zero4()
                    : actual.subtract(plan).divide(plan, 4, RoundingMode.HALF_UP);
            FeedExecVO.Daily item = new FeedExecVO.Daily();
            item.setDate(BeijingTime.format(date));
            item.setActualQty(FeedCalc.round2(actual));
            item.setPlanQty(FeedCalc.round2(plan));
            item.setPlannedMeals(dayTasks.size());
            item.setDoneMeals(done);
            item.setDeviationRatio(ratio);
            item.setOverThreshold(plan.signum() > 0 && ratio.abs().compareTo(threshold) > 0);
            daily.add(item);
        }
        return daily;
    }

    private String lineType(Range range, List<FeedTask> tasks) {
        if (range.batchIds().size() != 1) {
            return "STEP";
        }
        Set<LocalDate> dates = tasks.stream().map(FeedTask::getTaskDate).collect(Collectors.toSet());
        BigDecimal first = null;
        boolean flat = true;
        for (LocalDate date : dates) {
            BigDecimal plan = tasks.stream().filter(t -> date.equals(t.getTaskDate()))
                    .map(FeedTask::getPlanQty).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (first == null) {
                first = plan;
            } else if (first.compareTo(plan) != 0) {
                flat = false;
            }
        }
        return flat ? "FLAT" : "STEP";
    }

    private BigDecimal singlePlanDaily(Range range, List<FeedTask> tasks) {
        if (range.batchIds().size() != 1) {
            return null;
        }
        LocalDate latest = tasks.stream().map(FeedTask::getTaskDate).max(LocalDate::compareTo).orElse(null);
        if (latest == null) {
            return null;
        }
        return FeedCalc.round2(tasks.stream().filter(t -> latest.equals(t.getTaskDate()))
                .map(FeedTask::getPlanQty).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private List<FeedTask> tasks(Range range) {
        if (range.batchIds().isEmpty()) {
            return List.of();
        }
        return taskMapper.selectList(Wrappers.<FeedTask>lambdaQuery()
                .in(FeedTask::getBatchId, range.batchIds())
                .between(FeedTask::getTaskDate, range.start(), range.end())
                .notIn(FeedTask::getStatus, List.of(FeedCalc.TASK_CANCELLED, FeedCalc.TASK_IGNORED))
                .orderByAsc(FeedTask::getTaskDate)
                .orderByAsc(FeedTask::getMealNo));
    }

    private List<FeedRecord> records(Range range) {
        if (range.batchIds().isEmpty()) {
            return List.of();
        }
        return recordMapper.selectList(Wrappers.<FeedRecord>lambdaQuery()
                .in(FeedRecord::getBatchId, range.batchIds())
                .between(FeedRecord::getFeedDate, range.start(), range.end())
                .eq(FeedRecord::getStatus, "NORMAL")
                .orderByAsc(FeedRecord::getFeedDate)
                .orderByAsc(FeedRecord::getMealNo));
    }

    private Range resolve(FeedExecQuery query) {
        LocalDate today = BeijingTime.today();
        LocalDate yesterday = today.minusDays(1);
        String rangeName = query.getRange() == null ? "LAST_7" : query.getRange().trim().toUpperCase();
        if (!List.of("LAST_7", "LAST_30", "BATCH", "CUSTOM").contains(rangeName)) {
            throw FeedErrors.field("range", "统计范围不正确");
        }
        boolean all = Boolean.TRUE.equals(query.getAllBatches());
        if (all && query.getBatchId() != null) {
            throw FeedErrors.field("batch_id", "批次与全部批次只能选一个");
        }
        List<BatchInfo> batches;
        if (all) {
            batches = lookup.requireActiveBatches(lookup.farmId());
        } else {
            if (query.getBatchId() == null) {
                throw FeedErrors.field("batch_id", "请选择批次");
            }
            batches = List.of(lookup.requireOwnedBatch(query.getBatchId()));
        }
        List<Long> batchIds = batches.stream().map(BatchInfo::batchId).toList();
        LocalDate entry = batches.stream().map(BatchInfo::entryDate).filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo).orElse(today);
        LocalDate start;
        LocalDate end;
        switch (rangeName) {
            case "LAST_30" -> {
                start = today.minusDays(30);
                end = yesterday;
            }
            case "BATCH" -> {
                start = entry;
                end = yesterday;
            }
            case "CUSTOM" -> {
                start = query.getStartDate();
                end = query.getEndDate();
                if (start == null || end == null) {
                    throw FeedErrors.field("start_date", "请选择开始与结束日期");
                }
                if (end.isAfter(today)) {
                    throw FeedErrors.field("end_date", "结束日期不能晚于今天");
                }
                if (start.isBefore(entry)) {
                    throw FeedErrors.field("start_date", "开始日期不能早于入栏日期");
                }
                if (start.isAfter(end)) {
                    throw FeedErrors.field("start_date", "开始日期不能晚于结束日期");
                }
                if (ChronoUnit.DAYS.between(start, end) > 366) {
                    throw FeedErrors.field("end_date", "统计跨度不能超过 366 天");
                }
            }
            default -> {
                start = today.minusDays(7);
                end = yesterday;
            }
        }
        if (end.isBefore(start)) {
            end = start;
        }
        BigDecimal threshold = null;
        if (batchIds.size() == 1) {
            FeedPlan plan = planMapper.selectOne(Wrappers.<FeedPlan>lambdaQuery()
                    .eq(FeedPlan::getBatchId, batchIds.get(0)).last("LIMIT 1"));
            if (plan != null) {
                threshold = plan.getAbnormalThreshold();
            }
        }
        return new Range(batchIds, start, end, rangeName, threshold);
    }

    private static String penName(Map<Long, PenInfo> pens, Long penId) {
        PenInfo pen = pens.get(penId);
        return pen == null ? null : pen.penName();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal zero4() {
        return BigDecimal.ZERO.setScale(4);
    }

    private record Range(List<Long> batchIds, LocalDate start, LocalDate end, String range, BigDecimal threshold) {
    }
}
