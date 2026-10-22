package com.zhimu.slmp.account.model.request;

import androidx.annotation.NonNull;

/**
 * API-A13 短信验证后重新生成恢复码。
 */
public class RecoveryCodeRegenerateRequest {

    private final String smsCode;

    public RecoveryCodeRegenerateRequest(@NonNull String smsCode) {
        this.smsCode = smsCode;
    }

    /** 换成另一个验证码的副本（测试直连校验通过后提交给 Mock 用），相同时返回自身 */
    @NonNull
    public RecoveryCodeRegenerateRequest withSmsCode(@NonNull String code) {
        return code.equals(smsCode) ? this : new RecoveryCodeRegenerateRequest(code);
    }

    public String getSmsCode() {
        return smsCode;
    }
}
