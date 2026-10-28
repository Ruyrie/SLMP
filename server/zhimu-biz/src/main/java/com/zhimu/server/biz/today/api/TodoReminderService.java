package com.zhimu.server.biz.today.api;

/**
 * 来源模块按自己的提醒规则给待办发提醒时，先在模块03 的提醒计划（t_todo_remind）里登记一次：
 * 同一待办、同一阶段、同一周期只成功一次，避免定时任务重跑或多实例重复发消息；成功后累加 t_todo.remind_count。
 */
public interface TodoReminderService {

    /**
     * 登记一次已发送的提醒。
     *
     * @param remindStage   REMIND_STAGE：ADVANCE / DUE / OVERDUE / REPEAT
     * @param cycleKey      去重周期，例如 20261019
     * @param receiverScope RECEIVER_SCOPE：PEN_STAFF / PEN_ALL / MANAGERS
     * @param receiverCount 实际接收人数
     * @return 待办 ID；待办不存在、已关闭或本周期已登记过时返回 null（调用方不再发消息）
     */
    Long claim(Long farmId, String todoType, String dedupKey, String remindStage, String cycleKey,
               String receiverScope, int receiverCount);
}
