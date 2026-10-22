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

    public String getSmsCode() {
        return smsCode;
    }
}
