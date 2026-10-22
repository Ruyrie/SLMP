package com.zhimu.slmp.account.model.request;

import androidx.annotation.NonNull;

/**
 * API-A11 找回后修改密码。
 */
public class AfterRecoveryPasswordRequest {

    private final String newPassword;
    private final String confirmPassword;

    public AfterRecoveryPasswordRequest(@NonNull String newPassword, @NonNull String confirmPassword) {
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }
}
