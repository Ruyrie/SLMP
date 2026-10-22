package com.zhimu.slmp.account.model.vo;

import androidx.annotation.Nullable;

/**
 * A08 账号与安全：登录手机号、密码与恢复码时间、注销限制。
 */
public class AccountSecurityVO {

    @Nullable
    private String phoneMasked;
    @Nullable
    private String passwordUpdatedAt;
    @Nullable
    private String recoveryCodeGeneratedAt;
    private boolean cancelRestricted;
    @Nullable
    private String cancelAvailableDate;

    @Nullable
    public String getPhoneMasked() {
        return phoneMasked;
    }

    @Nullable
    public String getPasswordUpdatedAt() {
        return passwordUpdatedAt;
    }

    @Nullable
    public String getRecoveryCodeGeneratedAt() {
        return recoveryCodeGeneratedAt;
    }

    public boolean isCancelRestricted() {
        return cancelRestricted;
    }

    @Nullable
    public String getCancelAvailableDate() {
        return cancelAvailableDate;
    }
}
