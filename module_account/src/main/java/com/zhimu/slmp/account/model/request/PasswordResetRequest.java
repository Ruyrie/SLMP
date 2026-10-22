package com.zhimu.slmp.account.model.request;

import androidx.annotation.NonNull;

/**
 * API-A09 短信重置密码。
 */
public class PasswordResetRequest {

    private final String phone;
    private final String smsCode;
    private final String newPassword;

    public PasswordResetRequest(@NonNull String phone, @NonNull String smsCode, @NonNull String newPassword) {
        this.phone = phone;
        this.smsCode = smsCode;
        this.newPassword = newPassword;
    }

    public String getPhone() {
        return phone;
    }

    public String getSmsCode() {
        return smsCode;
    }

    public String getNewPassword() {
        return newPassword;
    }
}
