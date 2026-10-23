package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/** 消息详情（API-T16 data）：MessageBriefVO + 平铺的 OpenResultVO 字段 + payload。 */
@Getter
@Setter
public class MessageDetailVO extends MessageBriefVO {

    private String targetStatus;
    private String targetStatusText;
    private LinkVO link;
    private String deletedRecordType;
    private String pageTitle;
    private String deletedTitle;
    private String deletedDesc;
    private String denyTitle;
    private String denyDesc;
    private Integer totalUnread;
    /** 按 msg_type 原样返回的类型快照（键为 snake_case） */
    private Map<String, Object> payload;
}
