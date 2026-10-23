package com.zhimu.server.biz.account.enums;

import lombok.Getter;

/** 恢复码作废原因（t_recovery_code.revoke_reason） */
@Getter
public enum RecoveryCodeRevokeReason {
    REGENERATED("重新生成"),
    UNCONFIRMED("未确认保存"),
    ACCOUNT_RECOVERED("账号已找回"),
    ACCOUNT_CANCELLED("账号已注销");

    private final String text;

    RecoveryCodeRevokeReason(String text) {
        this.text = text;
    }
}
