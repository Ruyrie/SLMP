package com.zhimu.server.biz.farm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 所有权转让（t_farm_transfer） */
@Getter
@Setter
@FarmScoped
@TableName("t_farm_transfer")
public class FarmTransfer extends AuditEntity {

    private Long farmId;
    private Long fromUserId;
    private Long toUserId;
    private Long toMemberId;
    /** TRANSFER_STATUS：PENDING / ACCEPTED / REJECTED / CANCELED / EXPIRED */
    private String status;
    private Boolean pendingFlag;
    private LocalDateTime expiresAt;
    private LocalDateTime handledAt;
    private String cancelReason;
    private String requestId;
}
