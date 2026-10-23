package com.zhimu.server.biz.farm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 成员邀请（t_farm_invite） */
@Getter
@Setter
@FarmScoped
@TableName("t_farm_invite")
public class FarmInvite extends AuditEntity {

    private Long farmId;
    private String inviteePhone;
    private Long inviteeUserId;
    /** FARM_ROLE：MANAGER / STAFF */
    private String role;
    /** INVITE_STATUS：PENDING / ACCEPTED / REJECTED / EXPIRED / CANCELED */
    private String status;
    private Boolean pendingFlag;
    private LocalDateTime expiresAt;
    private LocalDateTime handledAt;
    private Long resendOfId;
    private Long memberId;
    private String cancelReason;
    private String requestId;
}
