package com.zhimu.server.biz.account.enums;

import lombok.Getter;

/** 恢复码状态（t_recovery_code.status） */
@Getter
public enum RecoveryCodeStatus {
    PENDING_CONFIRM("待确认保存"),
    ACTIVE("有效"),
    REVOKED("已作废");

    private final String text;

    RecoveryCodeStatus(String text) {
        this.text = text;
    }
}
