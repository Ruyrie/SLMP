package com.zhimu.server.biz.today.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 站内消息（t_message，每个接收人一行）。 */
@Getter
@Setter
@TableName("t_message")
public class Message extends AuditEntity {

    private Long userId;
    private Long farmId;
    /** FARM_ROLE 快照 */
    private String receiverRole;
    /** MSG_CATEGORY */
    private String category;
    /** MSG_TYPE */
    private String msgType;
    /** MSG_LEVEL */
    private String level;
    private String title;
    private String summary;
    private String tagCode;
    private String tagText;
    private String bizType;
    private Long bizId;
    private Long todoId;
    private String route;
    private String extraJson;
    private String mergeKey;
    private Integer mergeCount;
    private LocalDateTime sortTime;
    private LocalDateTime readAt;
    private Boolean hidden;
    private String hiddenReason;

    @TableLogic
    private Boolean deleted;

    private LocalDateTime deletedAt;
}
