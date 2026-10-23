package com.zhimu.server.integration.push;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.Map;

/**
 * 一条手机通知。
 * <pre>
 * PushMessage.builder()
 *         .title("东山猪场 · 3 号育肥舍早餐投喂已逾期")
 *         .content("请尽快完成投喂登记")
 *         .channel(PushChannels.TODO)
 *         .extra("route", "T04").extra("farm_id", 1001L).extra("biz_type", "TODO")
 *         .extra("biz_id", 88L).extra("message_id", 501L).extra("push_id", 9001L)
 *         .build();
 * </pre>
 */
@Getter
@Builder
public class PushMessage {

    /** 标题，带养殖场名 */
    private final String title;
    /** 正文，不超过 60 字（超出部分截断） */
    private final String content;
    /** 通知渠道 ID，见 {@link PushChannels}；同时写入 extras.channel */
    private final String channel;
    /** 附加字段：route、farm_id、biz_type、biz_id、message_id、push_id、todo_id 等 */
    @Singular
    private final Map<String, Object> extras;
}
