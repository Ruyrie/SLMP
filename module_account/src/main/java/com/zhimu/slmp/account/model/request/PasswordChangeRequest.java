package com.zhimu.slmp.account.model.request;

import androidx.annotation.NonNull;

/**
 * API-A17 修改密码。
 */
public class PasswordChangeRequest {

    private final String currentPassword;
    private final String newPassword;
    private final String confirmPassword;

    public PasswordChangeRequest(@NonNull String currentPassword, @NonNull String newPassword,
                                 @NonNull String confirmPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }
}
