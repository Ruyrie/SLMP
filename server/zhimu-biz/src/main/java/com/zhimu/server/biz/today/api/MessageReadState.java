package com.zhimu.server.biz.today.api;

import java.time.LocalDateTime;

/**
 * 消息的阅读与推送点开状态。
 *
 * @param readAt        用户在消息中心打开详情的北京时间
 * @param pushClickedAt 极光推送回执中的点击时间
 */
public record MessageReadState(LocalDateTime readAt, LocalDateTime pushClickedAt) {
}
