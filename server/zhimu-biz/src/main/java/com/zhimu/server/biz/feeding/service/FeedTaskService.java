package com.zhimu.server.biz.feeding.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.feeding.entity.FeedPlan;
import com.zhimu.server.biz.feeding.entity.FeedPlanMeal;
import com.zhimu.server.biz.feeding.entity.FeedPlanStage;
import com.zhimu.server.biz.feeding.entity.FeedRecord;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedPlanMapper;
import com.zhimu.server.biz.feeding.mapper.FeedPlanMealMapper;
import com.zhimu.server.biz.feeding.mapper.FeedPlanStageMapper;
import com.zhimu.server.biz.feeding.mapper.FeedRecordMapper;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.feeding.request.FeedTaskQuery;
import com.zhimu.server.biz.feeding.support.FeedCalc;
import com.zhimu.server.biz.feeding.support.FeedConvert;
import com.zhimu.server.biz.feeding.support.FeedErrors;
import com.zhimu.server.biz.feeding.support.FeedLookup;
import com.zhimu.server.biz.feeding.vo.FeedRecordVO;
import com.zhimu.server.biz.feeding.vo.FeedTaskVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** API-G01、G02、G03 与任务生成（技术准备文档 2.5.3、2.6.5、2.7.3–2.7.5）。 */
@Service
@RequiredArgsConstructor
public class FeedTaskService {

    private final FeedTaskMapper taskMapper;
    private final FeedPlanMapper planMapper;
    private final FeedPlanStageMapper stageMapper;
    private final FeedPlanMealMapper mealMapper;
    private final FeedRecordMapper recordMapper;
    private final FeedLookup lookup;

    /**
     * 手动补生成当前养殖场今天任务（所有者/场长）：为在养批次生成当天全部餐次，已存在则跳过。
     * 与每日 00:05 定时任务复用同一生成方法，不覆盖已有任务。
     */
    @Transactional
    public FeedTaskVO.GenerateToday generateToday() {
        FarmContext.FarmMember context = lookup.context();
        lookup.requireManager("只有所有者或场长可以生成今日饲喂任务");
        DailyGenerateResult result = generateDailyForFarm(context.farmId(), BeijingTime.today());
        FeedTaskVO.GenerateToday vo = new FeedTaskVO.GenerateToday();
        vo.setGenerated(result.generated());
        vo.setSkipped(result.skipped());
        return vo;
    }

    /** G01 今日饲喂指标与横幅 */
    public FeedTaskVO.TodaySummary todaySummary() {
        FarmContext.FarmMember context = lookup.context();
        LocalDate today = BeijingTime.today();
        LocalDateTime now = BeijingTime.now();
        List<FeedTask> tasks = todayTasks(context);
        List<FeedRecord> records = activeRecords(tasks);
        Map<Long, FeedRecord> recordByTask = records.stream()
                .collect(Collectors.toMap(FeedRecord::getTaskId, r -> r, (a, b) -> a));

        FeedTaskVO.TodaySummary vo = new FeedTaskVO.TodaySummary();
        vo.setDate(BeijingTime.format(today));
        vo.setWeekdayText(FeedCalc.weekdayText(today));
        Set<Long> penIds = tasks.stream().map(FeedTask::getPenId).collect(Collectors.toSet());
        vo.setPenCount(penIds.size());
        vo.setTaskTotal(tasks.size());

        int done = 0;
        int pending = 0;
        int overdue = 0;
        BigDecimal planTotal = BigDecimal.ZERO;
        BigDecimal actualTotal = BigDecimal.ZERO;
        FeedTask firstOverdue = null;
        for (FeedTask task : tasks) {
            String status = FeedCalc.effectiveStatus(task, now);
            planTotal = planTotal.add(nz(task.getPlanQty()));
            FeedRecord record = recordByTask.get(task.getId());
            if (record != null) {
                actualTotal = actualTotal.add(nz(record.getActualQty()));
            }
            if (FeedCalc.TASK_DONE.equals(status) || FeedCalc.TASK_MANUAL_DONE.equals(status)) {
                done++;
            } else if (FeedCalc.TASK_OVERDUE.equals(status)) {
                overdue++;
                if (firstOverdue == null || task.getFeedAt().isBefore(firstOverdue.getFeedAt())) {
                    firstOverdue = task;
                }
            } else if (FeedCalc.TASK_PENDING.equals(status)) {
                pending++;
            }
        }
        vo.setDoneCount(done);
        vo.setOverdueCount(overdue);
        vo.setPlanTotalQty(FeedCalc.round2(planTotal));
        vo.setActualTotalQty(FeedCalc.round2(actualTotal));
        vo.setStatusCounts(statusCounts(pending, done, overdue));
        vo.setFirstOverdue(firstOverdue == null ? null : firstOverdue(firstOverdue, now));
        vo.setPenOptions(penOptions(tasks));
        ShortageResult shortage = shortages(tasks, now);
        vo.setPlanShortage(shortage.shortages());
        vo.setPlanShortageMoreCount(shortage.moreCount());
        vo.setCanStockIn(context.isManagerView());
        vo.setCanManage(context.isManagerView());
        vo.setEmptyReason(emptyReason(context, tasks, shortage));
        return vo;
    }

    /** G02 今日任务列表 */
    public PageVO<FeedTaskVO.Item> today(FeedTaskQuery query) {
        FarmContext.FarmMember context = lookup.context();
        String status = query.getStatus() == null ? null : query.getStatus().trim().toUpperCase();
        if (status != null && !status.isEmpty() && !List.of("PENDING", "DONE", "OVERDUE").contains(status)) {
            throw FeedErrors.field("status", "任务状态筛选不正确");
        }
        Long penId = query.getPenId();
        if (penId != null) {
            lookup.checkPenScope(penId);
        }
        LocalDateTime now = BeijingTime.now();
        List<FeedTask> all = todayTasks(context);
        int pending = 0;
        int done = 0;
        int overdue = 0;
        List<FeedTask> matched = new ArrayList<>();
        for (FeedTask task : all) {
            String effective = FeedCalc.effectiveStatus(task, now);
            if (FeedCalc.TASK_PENDING.equals(effective)) {
                pending++;
            } else if (FeedCalc.TASK_OVERDUE.equals(effective)) {
                overdue++;
            } else if (FeedCalc.TASK_DONE.equals(effective) || FeedCalc.TASK_MANUAL_DONE.equals(effective)) {
                done++;
            }
            if (penId != null && !penId.equals(task.getPenId())) {
                continue;
            }
            if (status == null || status.isEmpty()) {
                matched.add(task);
            } else if ("PENDING".equals(status) && FeedCalc.TASK_PENDING.equals(effective)) {
                matched.add(task);
            } else if ("OVERDUE".equals(status) && FeedCalc.TASK_OVERDUE.equals(effective)) {
                matched.add(task);
            } else if ("DONE".equals(status)
                    && (FeedCalc.TASK_DONE.equals(effective) || FeedCalc.TASK_MANUAL_DONE.equals(effective))) {
                matched.add(task);
            }
        }
        List<FeedTaskVO.Item> items = toItems(matched, context);
        items.sort(Comparator.comparing(FeedTaskVO.Item::getFeedTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FeedTaskVO.Item::getPenId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FeedTaskVO.Item::getMealNo, Comparator.nullsLast(Comparator.naturalOrder())));
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, items.size());
        int to = Math.min(from + size, items.size());
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("pending", pending);
        tabs.put("done", done);
        tabs.put("overdue", overdue);
        return new PageVO<>(new ArrayList<>(items.subList(from, to)), items.size(), page, size).withTabCounts(tabs);
    }

    /** G02 登记表单上下文 */
    public FeedTaskVO.Form form(Long taskId) {
        FarmContext.FarmMember context = lookup.context();
        FeedTask task = requireTask(taskId);
        lookup.checkPenScope(task.getPenId());
        BatchInfo batch = lookup.requireOwnedBatch(task.getBatchId());
        LocalDateTime now = BeijingTime.now();
        String effective = FeedCalc.effectiveStatus(task, now);

        FeedTaskVO.Form vo = new FeedTaskVO.Form();
        vo.setTaskId(task.getId());
        vo.setTaskDate(BeijingTime.format(task.getTaskDate()));
        vo.setStatus(effective);
        vo.setStatusText(FeedCalc.statusText(effective));
        vo.setPenId(task.getPenId());
        vo.setPenName(lookup.penName(task.getPenId()));
        vo.setMealName(task.getMealName());
        vo.setFeedTime(formatTime(task.getFeedTime()));
        vo.setBatchId(task.getBatchId());
        vo.setBatchNo(batch.batchNo());
        vo.setBreedName(lookup.breedName(batch.breedId()));
        vo.setDayAge(FeedCalc.dayAge(batch, task.getTaskDate()));
        vo.setBatchStock(batch.currentCount());
        vo.setHeadCount(task.getHeadCount());
        vo.setDailyQtyPerHead(task.getDailyQtyPerHead());
        vo.setMealsPerDay(task.getMealsPerDay());
        vo.setPlanQty(task.getPlanQty());
        FeedPlan plan = planMapper.selectOne(Wrappers.<FeedPlan>lambdaQuery()
                .eq(FeedPlan::getBatchId, task.getBatchId()).last("LIMIT 1"));
        vo.setAbnormalThreshold(plan == null ? new BigDecimal("0.1000") : plan.getAbnormalThreshold());
        FeedStockPort.FeedBrief feed = lookup.feed(task.getFeedId());
        vo.setDefaultFeed(FeedConvert.option(feed));
        List<FeedRecordVO.OptionBrief> options = lookup.feeds().stream().map(FeedConvert::option).toList();
        vo.setFeedOptions(new ArrayList<>(options));

        List<UserBriefVO> members = lookup.memberOptions();
        UserBriefVO current = lookup.currentUserBrief();
        vo.setCurrentUser(current);
        if (context.isStaff()) {
            vo.setDefaultExecutor(current);
        } else {
            vo.setDefaultExecutor(findMember(members, task.getAssigneeId()));
            vo.setExecutorOptions(new ArrayList<>(members));
        }

        String block = blockReason(task, batch, effective, now, context);
        vo.setCanRegister(block == null);
        vo.setBlockReason(block);
        vo.setIsOverdue(FeedCalc.TASK_OVERDUE.equals(effective));
        vo.setBackfillEndAt(BeijingTime.format(task.getBackfillEndAt()));
        if (FeedCalc.TASK_DONE.equals(effective) || FeedCalc.TASK_MANUAL_DONE.equals(effective)) {
            vo.setRecordId(task.getRecordId());
        }
        vo.setCanStockIn(context.isManagerView());
        return vo;
    }

    /** 今天已生成的任务餐数（方案页提示） */
    public int countToday(Long batchId) {
        return Math.toIntExact(taskMapper.selectCount(Wrappers.<FeedTask>lambdaQuery()
                .eq(FeedTask::getBatchId, batchId)
                .eq(FeedTask::getTaskDate, BeijingTime.today())));
    }

    /** 指定养殖场在某天是否已有饲喂任务（启动补生成只在场内完全无任务时执行） */
    public boolean hasTasksOn(Long farmId, LocalDate date) {
        if (farmId == null || date == null) {
            return false;
        }
        return taskMapper.selectCount(Wrappers.<FeedTask>lambdaQuery()
                .eq(FeedTask::getFarmId, farmId)
                .eq(FeedTask::getTaskDate, date)) > 0;
    }

    /** 首次保存方案或转入新栏舍时，为今天投喂时间晚于当前时刻的餐次补生成任务 */
    @Transactional
    public void generateForBatch(BatchInfo batch, FeedPlan plan, List<FeedPlanStage> stages) {
        if (batch == null || !batch.active() || plan == null || stages == null || stages.isEmpty()) {
            return;
        }
        generateForFarm(batch.farmId(), batch, plan, stages, BeijingTime.today(), BeijingTime.now(), false);
    }

    /** 有在养批次的养殖场 ID（定时任务没有当前养殖场上下文时使用） */
    public List<Long> activeFarmIds() {
        return lookup.activeFarmIds();
    }

    /**
     * 定时任务：为指定养殖场的全部在养批次按方案生成指定日期的任务。
     * 幂等：同批次 + 栏舍 + 日期 + 餐次已存在时跳过，不覆盖已生成任务；返回新生成与跳过的槽位数。
     */
    @Transactional
    public DailyGenerateResult generateDailyForFarm(Long farmId, LocalDate date) {
        if (farmId == null || date == null) {
            return new DailyGenerateResult(0, 0);
        }
        int created = 0;
        int skipped = 0;
        for (BatchInfo batch : lookup.requireActiveBatches(farmId)) {
            FeedPlan plan = planMapper.selectOne(Wrappers.<FeedPlan>lambdaQuery()
                    .eq(FeedPlan::getBatchId, batch.batchId()).last("LIMIT 1"));
            if (plan == null) {
                continue;
            }
            List<FeedPlanStage> stages = stageMapper.selectList(Wrappers.<FeedPlanStage>lambdaQuery()
                    .eq(FeedPlanStage::getPlanId, plan.getId())
                    .orderByAsc(FeedPlanStage::getSortNo)
                    .orderByAsc(FeedPlanStage::getId));
            if (stages.isEmpty()) {
                continue;
            }
            DailyGenerateResult result = generateForFarm(farmId, batch, plan, stages, date, null, true);
            created += result.generated();
            skipped += result.skipped();
        }
        return new DailyGenerateResult(created, skipped);
    }

    /**
     * 按方案阶段与餐次生成任务。
     *
     * @param now           首存方案时只补生成投喂时间晚于此刻的餐次；定时任务传 null
     * @param includePast   是否生成投喂时间已经过去的餐次（定时任务在 00:05 执行，正常不会遇到；
     *                      服务停机后补跑时仍应生成当天全部餐次）
     */
    private DailyGenerateResult generateForFarm(Long farmId, BatchInfo batch, FeedPlan plan, List<FeedPlanStage> stages,
                                                LocalDate date, LocalDateTime now, boolean includePast) {
        int dayAge = FeedCalc.generationDayAge(batch, date);
        FeedPlanStage stage = stageForAge(stages, dayAge);
        if (stage == null) {
            return new DailyGenerateResult(0, 0);
        }
        List<FeedPlanMeal> meals = mealMapper.selectList(Wrappers.<FeedPlanMeal>lambdaQuery()
                .eq(FeedPlanMeal::getStageId, stage.getId())
                .orderByAsc(FeedPlanMeal::getMealNo));
        if (meals.isEmpty()) {
            return new DailyGenerateResult(0, 0);
        }
        int created = 0;
        int skipped = 0;
        for (com.zhimu.server.biz.feeding.support.FeedBatchPenRow pen : lookup.batchPens(batch)) {
            Integer headCount = pen.getStockCount();
            if (headCount == null || headCount <= 0) {
                continue;
            }
            for (FeedPlanMeal meal : meals) {
                if (meal.getFeedTime() == null) {
                    continue;
                }
                LocalDateTime feedAt = LocalDateTime.of(date, meal.getFeedTime());
                if (!includePast && now != null && !feedAt.isAfter(now)) {
                    continue;
                }
                long exists = taskMapper.selectCount(Wrappers.<FeedTask>lambdaQuery()
                        .eq(FeedTask::getBatchId, batch.batchId())
                        .eq(FeedTask::getPenId, pen.getPenId())
                        .eq(FeedTask::getTaskDate, date)
                        .eq(FeedTask::getMealNo, meal.getMealNo()));
                if (exists > 0) {
                    skipped++;
                    continue;
                }
                FeedTask task = new FeedTask();
                task.setFarmId(farmId);
                task.setBatchId(batch.batchId());
                task.setPenId(pen.getPenId());
                task.setPlanId(plan.getId());
                task.setStageId(stage.getId());
                task.setTaskDate(date);
                task.setMealNo(meal.getMealNo());
                task.setMealCode(meal.getMealCode());
                task.setMealName(meal.getMealName());
                task.setFeedTime(meal.getFeedTime());
                task.setFeedAt(feedAt);
                task.setDeadlineAt(feedAt.plusHours(2));
                task.setBackfillEndAt(date.plusDays(2).atStartOfDay());
                task.setFeedId(stage.getFeedId());
                task.setHeadCount(headCount);
                task.setDailyQtyPerHead(stage.getDailyQtyPerHead());
                task.setMealsPerDay(stage.getMealsPerDay());
                task.setPlanQty(FeedCalc.planQty(stage.getDailyQtyPerHead(), headCount, stage.getMealsPerDay()));
                task.setAssigneeId(lookup.firstAssignee(farmId, pen.getPenId()));
                task.setStatus(FeedCalc.TASK_PENDING);
                task.setOverdueNotified(false);
                task.setVersion(0);
                try {
                    taskMapper.insert(task);
                    created++;
                } catch (DuplicateKeyException e) {
                    // 方案保存与定时任务并发时由 uk_feed_task 兜底；已存在即视为幂等跳过
                    skipped++;
                }
            }
        }
        return new DailyGenerateResult(created, skipped);
    }

    /** 一批任务生成结果：generated 新生成，skipped 已存在而跳过 */
    public record DailyGenerateResult(int generated, int skipped) {
    }

    /** 任务（当前养殖场）；不存在返回 40401 */
    public FeedTask requireTask(Long taskId) {
        if (taskId == null) {
            throw FeedErrors.notFound();
        }
        FeedTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw FeedErrors.notFound();
        }
        return task;
    }

    public List<FeedTask> todayTasks(FarmContext.FarmMember context) {
        List<FeedTask> tasks = taskMapper.selectList(Wrappers.<FeedTask>lambdaQuery()
                .eq(FeedTask::getFarmId, context.farmId())
                .eq(FeedTask::getTaskDate, BeijingTime.today())
                .notIn(FeedTask::getStatus, List.of(FeedCalc.TASK_CANCELLED, FeedCalc.TASK_IGNORED))
                .orderByAsc(FeedTask::getFeedTime)
                .orderByAsc(FeedTask::getPenId)
                .orderByAsc(FeedTask::getMealNo));
        if (context.isStaff()) {
            Set<Long> pens = context.penIds();
            return tasks.stream().filter(task -> pens != null && pens.contains(task.getPenId())).toList();
        }
        return tasks;
    }

    private List<FeedTaskVO.Item> toItems(List<FeedTask> tasks, FarmContext.FarmMember context) {
        Map<Long, FeedRecord> records = activeRecords(tasks).stream()
                .collect(Collectors.toMap(FeedRecord::getTaskId, r -> r, (a, b) -> a));
        Map<Long, BatchInfo> batches = new HashMap<>();
        Map<Long, String> penNames = new HashMap<>();
        Map<Long, FeedStockPort.FeedBrief> feeds = new HashMap<>();
        Set<Long> userIds = new java.util.LinkedHashSet<>();
        for (FeedTask task : tasks) {
            userIds.add(task.getAssigneeId());
            FeedRecord record = records.get(task.getId());
            if (record != null) {
                userIds.add(record.getExecutorId());
            }
        }
        userIds.remove(null);
        Map<Long, UserBriefVO> members = lookup.memberBriefs(userIds);
        LocalDateTime now = BeijingTime.now();
        List<FeedTaskVO.Item> items = new ArrayList<>();
        for (FeedTask task : tasks) {
            FeedTaskVO.Item item = new FeedTaskVO.Item();
            item.setTaskId(task.getId());
            item.setTaskDate(BeijingTime.format(task.getTaskDate()));
            item.setMealNo(task.getMealNo());
            item.setMealCode(task.getMealCode());
            item.setMealName(task.getMealName());
            item.setFeedTime(formatTime(task.getFeedTime()));
            item.setPenId(task.getPenId());
            item.setPenName(penNames.computeIfAbsent(task.getPenId(), lookup::penName));
            item.setBatchId(task.getBatchId());
            BatchInfo batch = batches.computeIfAbsent(task.getBatchId(), id -> {
                try {
                    return lookup.requireOwnedBatch(id);
                } catch (RuntimeException e) {
                    return null;
                }
            });
            item.setBatchNo(batch == null ? null : batch.batchNo());
            item.setFeedId(task.getFeedId());
            FeedStockPort.FeedBrief feed = feeds.computeIfAbsent(task.getFeedId(), lookup::feed);
            item.setFeedName(feed == null ? null : feed.feedName());
            item.setLowStock(feed != null && feed.lowStock());
            item.setPlanQty(task.getPlanQty());
            UserBriefVO assignee = members.get(task.getAssigneeId());
            item.setAssigneeName(assignee == null ? null : assignee.getName());
            String effective = FeedCalc.effectiveStatus(task, now);
            FeedRecord record = records.get(task.getId());
            if (record != null) {
                item.setActualQty(record.getActualQty());
                item.setAbnormalType(record.getAbnormalType());
                item.setIsBackfill(record.getIsBackfill());
                item.setRecordId(record.getId());
                UserBriefVO executor = members.get(record.getExecutorId());
                item.setExecutorName(executor == null ? null : executor.getName());
                item.setRegisteredAt(BeijingTime.format(record.getCreatedAt()));
            }
            item.setStatus(effective);
            item.setStatusText(FeedCalc.statusText(effective));
            item.setOverdueMinutes(FeedCalc.TASK_OVERDUE.equals(effective)
                    ? FeedCalc.overdueMinutes(task, now) : null);
            item.setAction(action(effective, record, context));
            items.add(item);
        }
        return items;
    }

    private String action(String status, FeedRecord record, FarmContext.FarmMember context) {
        if (FeedCalc.TASK_PENDING.equals(status) || FeedCalc.TASK_OVERDUE.equals(status)) {
            return "REGISTER";
        }
        if ((FeedCalc.TASK_DONE.equals(status) || FeedCalc.TASK_MANUAL_DONE.equals(status))
                && record != null && context.isManagerView()) {
            return "EDIT";
        }
        return "VIEW";
    }

    private List<FeedRecord> activeRecords(List<FeedTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<Long> ids = tasks.stream().map(FeedTask::getId).toList();
        return recordMapper.selectList(Wrappers.<FeedRecord>lambdaQuery()
                .in(FeedRecord::getTaskId, ids)
                .eq(FeedRecord::getStatus, "NORMAL"));
    }

    private FeedTaskVO.StatusCounts statusCounts(int pending, int done, int overdue) {
        FeedTaskVO.StatusCounts counts = new FeedTaskVO.StatusCounts();
        counts.setPending(pending);
        counts.setDone(done);
        counts.setOverdue(overdue);
        return counts;
    }

    private FeedTaskVO.FirstOverdue firstOverdue(FeedTask task, LocalDateTime now) {
        FeedTaskVO.FirstOverdue vo = new FeedTaskVO.FirstOverdue();
        vo.setPenName(lookup.penName(task.getPenId()));
        vo.setMealName(task.getMealName());
        vo.setFeedTime(formatTime(task.getFeedTime()));
        vo.setOverdueMinutes(FeedCalc.overdueMinutes(task, now));
        return vo;
    }

    private List<FeedTaskVO.PenOption> penOptions(List<FeedTask> tasks) {
        Map<Long, List<FeedTask>> grouped = tasks.stream()
                .collect(Collectors.groupingBy(FeedTask::getPenId, LinkedHashMap::new, Collectors.toList()));
        List<FeedTaskVO.PenOption> options = new ArrayList<>();
        for (Map.Entry<Long, List<FeedTask>> entry : grouped.entrySet()) {
            FeedTaskVO.PenOption option = new FeedTaskVO.PenOption();
            option.setPenId(entry.getKey());
            option.setPenName(lookup.penName(entry.getKey()));
            option.setMealCount(entry.getValue().size());
            options.add(option);
        }
        options.sort(Comparator.comparing(FeedTaskVO.PenOption::getPenId, Comparator.nullsLast(Comparator.naturalOrder())));
        return options;
    }

    private ShortageResult shortages(List<FeedTask> tasks, LocalDateTime now) {
        Map<Long, List<FeedTask>> grouped = new LinkedHashMap<>();
        for (FeedTask task : tasks) {
            String effective = FeedCalc.effectiveStatus(task, now);
            if (FeedCalc.TASK_PENDING.equals(effective) || FeedCalc.TASK_OVERDUE.equals(effective)) {
                grouped.computeIfAbsent(task.getFeedId(), k -> new ArrayList<>()).add(task);
            }
        }
        List<FeedTaskVO.PlanShortage> shortages = new ArrayList<>();
        for (Map.Entry<Long, List<FeedTask>> entry : grouped.entrySet()) {
            FeedStockPort.FeedBrief feed = lookup.feed(entry.getKey());
            if (feed == null || feed.stockQty() == null) {
                continue;
            }
            List<FeedTask> feedTasks = entry.getValue();
            feedTasks.sort(Comparator.comparing(FeedTask::getFeedTime));
            BigDecimal need = BigDecimal.ZERO;
            BigDecimal cumulative = BigDecimal.ZERO;
            String restockBefore = null;
            for (FeedTask task : feedTasks) {
                cumulative = cumulative.add(nz(task.getPlanQty()));
                need = need.add(nz(task.getPlanQty()));
                if (restockBefore == null && cumulative.compareTo(feed.stockQty()) > 0) {
                    restockBefore = task.getMealName();
                }
            }
            if (need.compareTo(feed.stockQty()) > 0) {
                FeedTaskVO.PlanShortage vo = new FeedTaskVO.PlanShortage();
                vo.setFeedId(entry.getKey());
                vo.setFeedName(feed.feedName());
                vo.setStockQty(feed.stockQty());
                vo.setTodayNeedQty(FeedCalc.round2(need));
                vo.setShortQty(FeedCalc.round2(need.subtract(feed.stockQty())));
                vo.setRestockBeforeMealName(restockBefore == null && !feedTasks.isEmpty()
                        ? feedTasks.get(feedTasks.size() - 1).getMealName() : restockBefore);
                shortages.add(vo);
            }
        }
        shortages.sort(Comparator.comparing(FeedTaskVO.PlanShortage::getShortQty,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int more = Math.max(0, shortages.size() - 2);
        List<FeedTaskVO.PlanShortage> top = shortages.size() <= 2 ? shortages : shortages.subList(0, 2);
        return new ShortageResult(new ArrayList<>(top), more);
    }

    private String emptyReason(FarmContext.FarmMember context, List<FeedTask> tasks, ShortageResult shortage) {
        if (context.isStaff() && (context.penIds() == null || context.penIds().isEmpty())) {
            return "NO_PEN";
        }
        if (tasks.isEmpty()) {
            List<BatchInfo> active = lookup.requireActiveBatches(context.farmId());
            if (!active.isEmpty()) {
                List<Long> batchIds = active.stream().map(BatchInfo::batchId).toList();
                long plans = planMapper.selectCount(Wrappers.<FeedPlan>lambdaQuery().in(FeedPlan::getBatchId, batchIds));
                if (plans == 0) {
                    return "NO_PLAN";
                }
            }
            return "NO_TASK";
        }
        return null;
    }

    private String blockReason(FeedTask task, BatchInfo batch, String effective, LocalDateTime now,
                               FarmContext.FarmMember context) {
        if (!batch.active()) {
            return "BATCH_ENDED";
        }
        if (FeedCalc.TASK_DONE.equals(effective)) {
            return "DONE";
        }
        if (FeedCalc.TASK_CANCELLED.equals(effective)) {
            return "CANCELLED";
        }
        if (FeedCalc.TASK_IGNORED.equals(effective)) {
            return "IGNORED";
        }
        if (FeedCalc.TASK_MISSED.equals(effective)) {
            return "MISSED";
        }
        if (task.getBackfillEndAt() != null && !now.isBefore(task.getBackfillEndAt())) {
            return "MISSED";
        }
        return null;
    }

    private static UserBriefVO findMember(List<UserBriefVO> members, Long userId) {
        if (userId == null) {
            return null;
        }
        for (UserBriefVO member : members) {
            if (userId.equals(member.getUserId())) {
                return member;
            }
        }
        return null;
    }

    private static FeedPlanStage stageForAge(List<FeedPlanStage> stages, int dayAge) {
        for (FeedPlanStage stage : stages) {
            if (stage.getDayAgeStart() == null || dayAge < stage.getDayAgeStart()) {
                continue;
            }
            if (stage.getDayAgeEnd() == null || dayAge <= stage.getDayAgeEnd()) {
                return stage;
            }
        }
        return null;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String formatTime(LocalTime time) {
        return time == null ? null : time.format(BeijingTime.TIME);
    }

    private record ShortageResult(List<FeedTaskVO.PlanShortage> shortages, int moreCount) {
    }
}
