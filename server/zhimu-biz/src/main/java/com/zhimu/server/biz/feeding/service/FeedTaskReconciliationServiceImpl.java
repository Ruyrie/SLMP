package com.zhimu.server.biz.feeding.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.feeding.api.FeedTaskReconciliationService;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.feeding.support.FeedCalc;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** 批次结束后撤销无法执行的饲喂任务，保留已登记和历史未执行记录。 */
@Service
@RequiredArgsConstructor
public class FeedTaskReconciliationServiceImpl implements FeedTaskReconciliationService {
    public static final String BATCH_ENDED_REASON = "批次已结束或存栏已清空";

    private final FeedTaskMapper taskMapper;
    private final BatchQueryService batchQuery;
    // 待办查询也使用本服务；延迟获取写入端避免构造依赖环。
    private final ObjectProvider<TodoSourceWriteService> writerProvider;

    @Override
    @Transactional
    public void reconcileFarm(Long farmId) {
        if (farmId == null) return;
        TenantSkip.run(() -> reconcile(farmId));
    }

    private void reconcile(Long farmId) {
        var tasks = taskMapper.selectList(Wrappers.<FeedTask>lambdaQuery()
                .eq(FeedTask::getFarmId, farmId)
                .isNull(FeedTask::getRecordId)
                .and(q -> q.in(FeedTask::getStatus, FeedCalc.TASK_PENDING, FeedCalc.TASK_OVERDUE)
                        .or(c -> c.eq(FeedTask::getStatus, FeedCalc.TASK_CANCELLED)
                                .eq(FeedTask::getCloseReason, BATCH_ENDED_REASON)))
                .orderByAsc(FeedTask::getId));
        Map<Long, BatchInfo> batches = new HashMap<>();
        LocalDateTime now = BeijingTime.now();
        for (FeedTask task : tasks) {
            if (!farmId.equals(task.getFarmId()) || task.getRecordId() != null) continue;
            boolean open = FeedCalc.TASK_PENDING.equals(task.getStatus())
                    || FeedCalc.TASK_OVERDUE.equals(task.getStatus());
            boolean autoCancelled = FeedCalc.TASK_CANCELLED.equals(task.getStatus())
                    && BATCH_ENDED_REASON.equals(task.getCloseReason());
            if (!open && !autoCancelled) continue;
            if (!batches.containsKey(task.getBatchId())) {
                batches.put(task.getBatchId(), findBatch(task.getBatchId()));
            }
            BatchInfo batch = batches.get(task.getBatchId());
            if (batch == null || !farmId.equals(batch.farmId())) continue;
            boolean ended = "ENDED".equals(batch.status())
                    || (batch.active() && Integer.valueOf(0).equals(batch.currentCount()));
            if (ended) {
                if (open && taskMapper.update(new FeedTask(), Wrappers.<FeedTask>lambdaUpdate()
                        .eq(FeedTask::getId, task.getId()).eq(FeedTask::getFarmId, farmId)
                        .eq(FeedTask::getStatus, task.getStatus()).isNull(FeedTask::getRecordId)
                        .set(FeedTask::getStatus, FeedCalc.TASK_CANCELLED)
                        .set(FeedTask::getCloseReason, BATCH_ENDED_REASON)
                        .set(FeedTask::getHandledBy, 0L).set(FeedTask::getHandledAt, now)
                        .setSql("version = version + 1")) == 0) continue;
                writerProvider.getObject().ignoreFromSource(farmId, FeedTodoSource.TODO_TYPE,
                        FeedTodoSource.dedupKey(task.getId()), BATCH_ENDED_REASON);
            } else if (autoCancelled && batch.active() && batch.currentCount() != null
                    && batch.currentCount() > 0 && task.getBackfillEndAt() != null
                    && now.isBefore(task.getBackfillEndAt())) {
                // 出栏作废恢复存栏时，只恢复本逻辑取消的、仍可执行的任务。
                String status = task.getDeadlineAt() != null && now.isAfter(task.getDeadlineAt())
                        ? FeedCalc.TASK_OVERDUE : FeedCalc.TASK_PENDING;
                int changed = taskMapper.update(new FeedTask(), Wrappers.<FeedTask>lambdaUpdate()
                        .eq(FeedTask::getId, task.getId()).eq(FeedTask::getFarmId, farmId)
                        .eq(FeedTask::getStatus, FeedCalc.TASK_CANCELLED)
                        .eq(FeedTask::getCloseReason, BATCH_ENDED_REASON).isNull(FeedTask::getRecordId)
                        .set(FeedTask::getStatus, status).set(FeedTask::getCloseReason, null)
                        .set(FeedTask::getHandledBy, null).set(FeedTask::getHandledAt, null)
                        .setSql("version = version + 1"));
                if (changed > 0) {
                    writerProvider.getObject().reopenAutoIgnoredFromSource(farmId,
                            FeedTodoSource.TODO_TYPE, FeedTodoSource.dedupKey(task.getId()), BATCH_ENDED_REASON);
                }
            }
        }
    }

    private BatchInfo findBatch(Long id) {
        if (id == null) return null;
        try {
            return batchQuery.requireBatch(id);
        } catch (BizException e) {
            if (e.getCode() == ErrorCode.NOT_FOUND.getCode()) return null;
            throw e;
        }
    }
}
