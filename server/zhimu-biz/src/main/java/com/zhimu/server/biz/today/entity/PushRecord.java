package com.zhimu.server.biz.today.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 推送记录与延后队列（t_push_record）；本批只落待办/消息推送，Web 与每日汇总后续接。 */
@Getter
@Setter
@TableName("t_push_record")
public class PushRecord extends BaseEntity {

    private Long userId;
    private Long farmId;
    private Long messageId;
    private Long todoId;
    /** PUSH_TYPE */
    private String pushType;
    /** PUSH_CHANNEL */
    private String channel;
    private String notifyChannel;
    private String title;
    private String content;
    private String route;
    private String bizType;
    private Long bizId;
    private Boolean bypassQuiet;
    private String dedupKey;
    private LocalDateTime planSendAt;
    /** PUSH_STATUS */
    private String status;
    private Integer retryCount;
    private LocalDateTime sentAt;
    private LocalDateTime clickedAt;
    private String jpushMsgId;
    private String failReason;
}
