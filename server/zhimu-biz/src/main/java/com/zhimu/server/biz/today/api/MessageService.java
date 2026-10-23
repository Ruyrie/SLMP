package com.zhimu.server.biz.today.api;

import com.zhimu.server.common.enums.BizType;

/**
 * 站内消息与推送（模块03 实现，第 3 批）。在此之前由 biz.today.support.TemporaryMessageService 只写日志。
 * 调用方在业务事务提交后调用（@TransactionalEventListener AFTER_COMMIT）。
 */
public interface MessageService {

    void send(MessageCommand command);

    /** 业务记录被删除或作废时隐藏相关消息（例如邀请撤回） */
    void hideByBiz(BizType bizType, Long bizId);

    /** 成员离开养殖场：隐藏该成员在该场的消息 */
    void hideFarmMessagesForUser(Long farmId, Long userId);
}