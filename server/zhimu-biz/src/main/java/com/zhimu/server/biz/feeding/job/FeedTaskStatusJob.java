package com.zhimu.server.biz.feeding.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.feeding.service.FeedTodoSource;
import com.zhimu.server.biz.feeding.support.FeedCalc;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.lock.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模块06 未执行饲喂任务流转（技术准备文档 2.7.3）：每 10 分钟把补登期限已过、仍未登记的
 * PENDING / OVERDUE 任务置为 MISSED，并写 close_reason = 超过补登期限。
 * <p>
 * 逾期展示仍由 {@link FeedCalc#effectiveStatus} 实时计算，本任务只处理“补登窗口结束”的落库流转；
 * 更新条件带状态，不会影响 DONE / MANUAL_DONE / IGNORED / CANCELLED。对应待办自动忽略。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedTaskStatusJob {

    static final int BATCH = 500;

    private final FeedTaskMapper taskMapper;
    private final TodoSourceWriteService todoWriter;

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Shanghai")
    @JobLock("feed-task-status")
    public void run() {
        markMissed(BeijingTime.now());
    }

    /** 指定时刻执行（单元测试用固定时钟；返回流转条数） */
    int markMissed(LocalDateTime now) {
        List<FeedTask> tasks = taskMapper.selectList(Wrappers.<FeedTask>lambdaQuery()
                .in(FeedTask::getStatus, FeedCalc.TASK_PENDING, FeedCalc.TASK_OVERDUE)
                .isNotNull(FeedTask::getBackfillEndAt)
                .le(FeedTask::getBackfillEndAt, now)
                .orderByAsc(FeedTask::getBackfillEndAt)
                .last("LIMIT " + BATCH));
        int count = 0;
        for (FeedTask task : tasks) {
            // 查询已过滤，仍显式校验一次，避免调用方传入的数据影响已关闭任务
            if (!FeedCalc.TASK_PENDING.equals(task.getStatus())
                    && !FeedCalc.TASK_OVERDUE.equals(task.getStatus())) {
                continue;
            }
            if (task.getBackfillEndAt() == null || now.isBefore(task.getBackfillEndAt())) {
                continue;
            }
            int rows = taskMapper.update(new FeedTask(), Wrappers.<FeedTask>lambdaUpdate()
                    .eq(FeedTask::getId, task.getId())
                    .in(FeedTask::getStatus, FeedCalc.TASK_PENDING, FeedCalc.TASK_OVERDUE)
                    .set(FeedTask::getStatus, FeedCalc.TASK_MISSED)
                    .set(FeedTask::getCloseReason, FeedCalc.CLOSE_REASON_BACKFILL_EXPIRED));
            if (rows == 0) {
                continue;
            }
            count += rows;
            todoWriter.ignoreFromSource(task.getFarmId(), FeedTodoSource.TODO_TYPE,
                    FeedTodoSource.dedupKey(task.getId()), FeedCalc.CLOSE_REASON_BACKFILL_EXPIRED);
        }
        if (count > 0) {
            log.info("未执行饲喂任务流转 MISSED：{} 条", count);
        }
        return count;
    }
}
