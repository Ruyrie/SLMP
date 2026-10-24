package com.zhimu.server.biz.today.api;

import com.zhimu.server.common.enums.BizType;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    /**
     * 隐藏某业务下尚未读取的消息（平台通知撤回，管理端-02）：已读消息保留，未读消息置 hidden。
     * @return 更新的消息条数
     */
    int hideUnreadByBiz(BizType bizType, Long bizId, String hiddenReason);

    /** 某业务写出的站内消息（管理端-02 通知送达明细与统计用） */
    List<MessageRef> listByBiz(BizType bizType, Long bizId);

    /** 按 message_id 批量读取阅读与推送点开状态，最多 1000 条 */
    Map<Long, MessageReadState> listReadState(Collection<Long> messageIds);
}