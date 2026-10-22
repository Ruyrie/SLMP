package com.zhimu.slmp.account.model.request;

import androidx.annotation.NonNull;

/**
 * API-A10 凭恢复码提交找回申请。
 */
public class AccountRecoveryRequest {

    private final String oldPhone;
    private final String recoveryCode;
    private final String newPhone;
    private final String newPhoneSmsCode;
    private final String description;

    public AccountRecoveryRequest(@NonNull String oldPhone, @NonNull String recoveryCode, @NonNull String newPhone,
                                  @NonNull String newPhoneSmsCode, @NonNull String description) {
        this.oldPhone = oldPhone;
        this.recoveryCode = recoveryCode;
        this.newPhone = newPhone;
        this.newPhoneSmsCode = newPhoneSmsCode;
        this.description = description;
    }

    /** 换成另一个新手机号验证码的副本（测试直连校验通过后提交给 Mock 用），相同时返回自身 */
    @NonNull
    public AccountRecoveryRequest withNewPhoneSmsCode(@NonNull String code) {
        return code.equals(newPhoneSmsCode) ? this
                : new AccountRecoveryRequest(oldPhone, recoveryCode, newPhone, code, description);
    }

    public String getOldPhone() {
        return oldPhone;
    }

    public String getRecoveryCode() {
        return recoveryCode;
    }

    public String getNewPhone() {
        return newPhone;
    }

    public String getNewPhoneSmsCode() {
        return newPhoneSmsCode;
    }

    public String getDescription() {
        return description;
    }
}
