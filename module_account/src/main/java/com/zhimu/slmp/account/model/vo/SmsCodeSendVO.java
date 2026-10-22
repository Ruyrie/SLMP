package com.zhimu.slmp.account.model.vo;

import androidx.annotation.Nullable;

/**
 * API-A01、A02 发送验证码的结果（不含验证码本身）。
 */
public class SmsCodeSendVO {

    /** 138****6620 */
    @Nullable
    private String phoneMasked;
    /** 冷却秒数，60 */
    private int cooldownSeconds;
    /** 有效期秒数，300 */
    private int expireSeconds;

    @Nullable
    public String getPhoneMasked() {
        return phoneMasked;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public int getExpireSeconds() {
        return expireSeconds;
    }
}
