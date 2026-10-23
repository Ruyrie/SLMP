package com.zhimu.server.biz.account.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 恢复码校验记录（t_recovery_verify_log）：只追加，不修改。 */
@Getter
@Setter
@TableName("t_recovery_verify_log")
public class RecoveryVerifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String oldPhone;
    private String result;
    private Integer failCountAfter;
    private Integer remainingAttempts;
    private LocalDateTime lockUntil;
    private Long recoveryId;
    private String submitChannel;
    private Long proxyAdminId;
    private String ip;
    private String ipRegion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
