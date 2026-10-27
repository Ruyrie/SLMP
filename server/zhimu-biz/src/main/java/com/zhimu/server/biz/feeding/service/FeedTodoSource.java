package com.zhimu.server.biz.feeding.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.feeding.support.FeedBatchRow;
import com.zhimu.server.biz.feeding.support.FeedCalc;
import com.zhimu.server.biz.feeding.support.FeedLookup;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模块06 的待办来源（模块03 SPI）：把当天未完成的饲喂任务映射为 WORK 类待办。
 * <p>
 * 一个饲喂任务一条待办，幂等键 {@code FEED_TASK:{task_id}}；登记投喂后由
 * {@link FeedRecordService} 调 {@link TodoSourceWriteService#completeFromSource} 自动完成，
 * 超过补登期限后由 {@link com.zhimu.server.biz.feeding.job.FeedTaskStatusJob} 自动忽略。
 * 去向由模块03 {@code TodoLinks} 按 todoType = FEEDING 计算（App G02 登记投喂）。
 */
@Service
@RequiredArgsConstructor
public class FeedTodoSource implements TodoSource {

    public static final String TODO_TYPE = "FEEDING";
    public static final String CATEGORY = "WORK";
    public static final String BIZ_TYPE = "FEED_TASK";
    public static final String RECEIVER_SCOPE = "PEN_STAFF";
    public static final String TIME_TYPE = "AT_TIME";

    private final FeedTaskMapper taskMapper;
    private final FeedLookup lookup;
    private final FeedStockPort feedStockPort;
    private final TodoSourceWriteService todoWriter;
    private final com.zhimu.server.biz.feeding.api.FeedTaskReconciliationService reconciliation;

    @Override
    public String moduleName() {
        return "feeding";
    }

    /** 为养殖场指定日期生成待办；已存在的只刷新展示字段，返回涉及的待办 ID */
    @Override
    public List<Long> generate(TodoSource.TodoSourceContext context) {
        if (context == null || context.getFarmId() == null) {
            return List.of();
        }
        Long farmId = context.getFarmId();
        reconciliation.reconcileFarm(farmId);
        LocalDate date = context.getDate() == null ? BeijingTime.today() : context.getDate();
        List<FeedTask> tasks = taskMapper.selectList(Wrappers.<FeedTask>lambdaQuery()
                .eq(FeedTask::getFarmId, farmId)
                .eq(FeedTask::getTaskDate, date)
                .in(FeedTask::getStatus, FeedCalc.TASK_PENDING, FeedCalc.TASK_OVERDUE)
                .orderByAsc(FeedTask::getFeedTime)
                .orderByAsc(FeedTask::getPenId)
                .orderByAsc(FeedTask::getMealNo));
        if (tasks.isEmpty()) {
            return List.of();
        }
        Set<Long> penIds = new LinkedHashSet<>();
        Set<Long> userIds = new LinkedHashSet<>();
        for (FeedTask task : tasks) {
            penIds.add(task.getPenId());
            if (task.getAssigneeId() != null) {
                userIds.add(task.getAssigneeId());
            }
        }
        Map<Long, PenInfo> pens = lookup.pens(penIds);
        Map<Long, UserBriefVO> members = lookup.memberBriefs(farmId, userIds);
        Map<Long, FeedBatchRow> batches = new HashMap<>();
        Map<Long, FeedStockPort.FeedBrief> feeds = new HashMap<>();
        LocalDateTime now = BeijingTime.now();

        List<TodoSource.TodoDraft> drafts = new ArrayList<>();
        for (FeedTask task : tasks) {
            if (!unfinished(task) || backfillExpired(task, now)) {
                continue;
            }
            String penName = penName(pens.get(task.getPenId()));
            FeedBatchRow batch = batches.computeIfAbsent(task.getBatchId(), lookup::batchBasics);
            FeedStockPort.FeedBrief feed = feeds.computeIfAbsent(task.getFeedId(),
                    id -> feedStockPort.getFeed(farmId, id));
            UserBriefVO assignee = members.get(task.getAssigneeId());
            drafts.add(TodoSource.TodoDraft.builder()
                    .todoType(TODO_TYPE)
                    .category(CATEGORY)
                    .penId(task.getPenId())
                    .batchId(task.getBatchId())
                    .penName(penName)
                    .batchNo(batch == null ? null : batch.getBatchNo())
                    .bizType(BIZ_TYPE)
                    .bizId(task.getId())
                    .dedupKey(dedupKey(task.getId()))
                    .title(title(penName, task.getMealName()))
                    .contentText(content(feed, task.getPlanQty()))
                    .receiverScope(RECEIVER_SCOPE)
                    .assigneeId(task.getAssigneeId())
                    .assigneeName(assignee == null ? null : assignee.getName())
                    .dueDate(task.getTaskDate())
                    .timeType(TIME_TYPE)
                    .dueTime(task.getFeedTime())
                    .overdueAt(task.getDeadlineAt())
                    .build());
        }
        return drafts.isEmpty() ? List.of() : todoWriter.upsertFromSource(farmId, drafts);
    }

    /** 待办去重键：一个饲喂任务一条待办 */
    public static String dedupKey(Long taskId) {
        return BIZ_TYPE + ":" + taskId;
    }

    private static boolean unfinished(FeedTask task) {
        return FeedCalc.TASK_PENDING.equals(task.getStatus()) || FeedCalc.TASK_OVERDUE.equals(task.getStatus());
    }

    private static boolean backfillExpired(FeedTask task, LocalDateTime now) {
        return task.getBackfillEndAt() != null && !now.isBefore(task.getBackfillEndAt());
    }

    private static String penName(PenInfo pen) {
        return pen == null || pen.penName() == null ? "栏舍" : pen.penName();
    }

    private static String title(String penName, String mealName) {
        return penName + " · " + (mealName == null ? "饲喂" : mealName + "饲喂");
    }

    private static String content(FeedStockPort.FeedBrief feed, BigDecimal planQty) {
        String feedName = feed == null || feed.feedName() == null ? "饲料" : feed.feedName();
        return feedName + " · 计划 " + qty(planQty) + " 千克";
    }

    private static String qty(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
