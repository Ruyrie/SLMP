package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 消息上的可处理动作：邀请（INVITE）与转让（TRANSFER）。 */
@Getter
@Setter
public class MessageActionVO {

    /** INVITE / TRANSFER */
    private String actionType;
    private Long actionId;
    /** PENDING / ACCEPTED / REJECTED / EXPIRED / CANCELED */
    private String status;
    private String statusText;
    private String roleText;
    private LocalDateTime expireAt;
    private Boolean canAccept;
}
