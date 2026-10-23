package com.zhimu.server.biz.account.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 恢复码（t_recovery_code）。只存 BCrypt 摘要；live_flag 未作废时为 1，作废时置空（唯一索引的 NULL 语义）。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_recovery_code")
public class RecoveryCode extends AuditEntity {

    private Long userId;
    private String codeHash;
    private String status;
    private String scene;
    @TableField("is_regenerated")
    private Boolean regenerated;
    private String clientType;
    /** 未作废时 1，作废时 null */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer liveFlag;
    private LocalDateTime confirmedAt;
    private LocalDateTime revokedAt;
    private String revokeReason;
}
