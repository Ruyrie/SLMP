package com.zhimu.server.biz.account.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 用户账号（t_user）。全局表，不带 farm_id；不设 deleted，注销以 status = CANCELLED 表示。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_user")
public class User extends AuditEntity {

    private String phone;
    private String passwordHash;
    private String name;
    /** 头像文件 ID（t_file.id，scene = AVATAR） */
    private Long avatarFileId;
    private String status;
    private Integer tokenVersion;
    private String tokenRevokeReason;
    private LocalDateTime tokenRevokedAt;
    private Boolean mustChangePassword;
    private LocalDateTime tempPasswordExpireAt;
    private LocalDateTime passwordUpdatedAt;
    private LocalDateTime recoveredAt;
    private Integer recoveryFailCount;
    private LocalDateTime recoveryFailFirstAt;
    private LocalDateTime recoveryLockUntil;
    private Long lastFarmId;
    private String registerClient;
    private String agreementVersion;
    private LocalDateTime agreedAt;
    private LocalDateTime lastLoginAt;
    private String lastLoginClient;
    private LocalDateTime lastActiveAt;
    private LocalDateTime disabledAt;
    private Long disabledBy;
    private String disabledReason;
    private LocalDateTime cancelledAt;
    /** 修改姓名时加 1，API-A15 返回、API-A16 用作乐观锁，不复用 version */
    private Integer profileVersion;

    @Version
    private Integer version;
}
