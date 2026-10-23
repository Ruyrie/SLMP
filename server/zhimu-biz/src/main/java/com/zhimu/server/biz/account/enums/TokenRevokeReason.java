package com.zhimu.server.biz.account.enums;

import lombok.Getter;

/** token_version 变化原因（t_user.token_revoke_reason，40104 的 data.reason） */
@Getter
public enum TokenRevokeReason {
    PASSWORD_CHANGED("密码已修改"),
    ACCOUNT_RECOVERED("账号已找回"),
    ACCOUNT_DISABLED("已停用"),
    ACCOUNT_CANCELLED("已注销"),
    EXPIRED("已过期");

    private final String text;

    TokenRevokeReason(String text) {
        this.text = text;
    }
}
