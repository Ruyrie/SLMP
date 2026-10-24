package com.zhimu.server.admin.audit.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 找回详情的恢复码校验记录。 */
@Getter
@Setter
public class RecoveryVerifyLogRow {

    private Long id;
    private LocalDateTime checkedAt;
    private String oldPhone;
    private String result;
    private Integer remainingAttempts;
    private LocalDateTime lockUntil;
    private String submitChannel;
    private Long proxyAdminId;
    private String proxyAdminName;
}
