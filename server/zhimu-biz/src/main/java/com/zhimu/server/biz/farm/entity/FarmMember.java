package com.zhimu.server.biz.farm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 养殖场成员（t_farm_member） */
@Getter
@Setter
@FarmScoped
@TableName("t_farm_member")
public class FarmMember extends AuditEntity {

    private Long farmId;
    private Long userId;
    /** FARM_ROLE：OWNER / MANAGER / STAFF */
    private String role;
    /** MEMBER_STATUS：ACTIVE / REMOVED / QUIT / ACCOUNT_CANCELED */
    private String status;
    private Boolean activeFlag;
    /** JOIN_TYPE：CREATE / INVITE */
    private String joinType;
    private Long inviteId;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private Long removedBy;
    private LocalDateTime lastSwitchAt;

    @Version
    private Integer version;

    private Long updatedBy;
}
