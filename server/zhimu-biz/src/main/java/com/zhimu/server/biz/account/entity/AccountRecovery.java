package com.zhimu.server.biz.account.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 凭恢复码找回申请（t_account_recovery）。pending_flag 待审核时为 1，处理后置空。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_account_recovery")
public class AccountRecovery extends AuditEntity {

    private Long userId;
    private String oldPhone;
    private String newPhone;
    private LocalDateTime newPhoneVerifiedAt;
    private String description;
    private Integer codeAttemptNo;
    private Integer codeFailCount;
    private String submitChannel;
    private Long proxyAdminId;
    private String submitIp;
    private String submitIpRegion;
    private String submitDevice;
    private String status;
    /** 待审核时 1，处理后 null */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer pendingFlag;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String verifyMethod;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String handleNote;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String handledByType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long handledBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime handledAt;
    private String resultSmsStatus;
    private Integer resultSmsRetryCount;
    private LocalDateTime resultSmsNextAt;

    @Version
    private Integer version;
}
