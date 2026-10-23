package com.zhimu.server.biz.account.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/** 账号注销记录与分阶段清理进度（t_account_cancel）。 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_account_cancel")
public class AccountCancel extends AuditEntity {

    private Long userId;
    private String phoneMasked;
    private String phoneHash;
    private String retainedFarmsJson;
    private String deletedFarmsJson;
    private String exitedFarmsJson;
    private String withdrawnJson;
    private String clientType;
    private String ip;
    private String ipRegion;
    private LocalDateTime phoneReleaseAt;
    private LocalDateTime farmCleanupAt;
    private LocalDateTime personalCleanedAt;
    private LocalDateTime phoneReleasedAt;
    private LocalDateTime farmCleanedAt;
    private String cleanupStatus;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime nextRunAt;
}
