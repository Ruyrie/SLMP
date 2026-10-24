package com.zhimu.server.biz.today.api;

import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.MsgLevel;
import com.zhimu.server.common.enums.MsgType;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Map;
import java.util.Set;

/**
 * 写一条站内消息（每个接收人一条）并按提醒设置推送。由模块03 MessageService 处理。
 *
 * @param route  客户端点开后打开的页面编号，例如 F05、T07
 * @param extras 推送附加字段与消息详情需要的额外数据（键用 snake_case），例如 invite_id、transfer_id
 */
@Value
@Builder
public class MessageCommand {
    MsgType msgType;
    @Builder.Default
    MsgLevel level = MsgLevel.NORMAL;
    /** 消息所属养殖场；与养殖场无关的消息（例如账号找回结果）为 null */
    Long farmId;
    @Singular
    Set<Long> receiverUserIds;
    String title;
    String summary;
    /** 详情正文，可空 */
    String content;
    BizType bizType;
    Long bizId;
    /** 合并键：同一接收人同一键只保留一条；平台通知用 NOTICE:{notice_id} */
    String mergeKey;
    String route;
    @Singular
    Map<String, Object> extras;
}