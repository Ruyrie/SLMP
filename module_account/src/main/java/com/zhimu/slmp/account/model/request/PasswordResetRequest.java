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

    /** 换成另一个验证码的副本（测试直连校验通过后提交给 Mock 用），相同时返回自身 */
    @NonNull
    public PasswordResetRequest withSmsCode(@NonNull String code) {
        return code.equals(smsCode) ? this : new PasswordResetRequest(phone, code, newPassword);
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
