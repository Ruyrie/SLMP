package com.zhimu.server.biz.today.api;

import java.time.LocalDateTime;

/**
 * 一条站内消息的引用（管理端-02 通知分发与统计用）。
 *
 * @param messageId t_message.id
 * @param userId    接收账号
 * @param farmId    消息所属养殖场（平台通知按条件/全部用户时为空）
 * @param readAt    阅读时间
 */
public record MessageRef(Long messageId, Long userId, Long farmId, LocalDateTime readAt) {
}
