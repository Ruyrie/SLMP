package com.zhimu.server.admin.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 平台管理员（t_admin，管理端-04 2.3.1）。管理员不删除，只停用，因此没有 deleted 列。
 * token_version 在停用、重置密码、改密时加 1，使会话立即失效。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_admin")
public class Admin extends AuditEntity {

    private String username;
    private String name;
    private String phone;
    private String passwordHash;
    private Long roleId;
    private String status;
    private Boolean mustChangePassword;
    private LocalDateTime tempPasswordExpireAt;
    private LocalDateTime passwordUpdatedAt;
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;
    private Integer tokenVersion;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private String lastLoginRegion;
    private LocalDateTime disabledAt;

    @Version
    private Integer version;
}
