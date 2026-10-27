package com.zhimu.server.biz.feeding.api;

/** 读取待办或生成饲喂任务前，同步批次状态与未执行的饲喂任务。 */
public interface FeedTaskReconciliationService {
    void reconcileFarm(Long farmId);
}
