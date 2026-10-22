package com.zhimu.slmp.common.sms;

import androidx.annotation.Nullable;

/**
 * SendSmsVerifyCode 成功的结果（不含验证码本身）。
 */
public final class SmsSendResult {

    @Nullable
    private final String bizId;
    @Nullable
    private final String requestId;
    private final int cooldownSeconds;
    private final int expireSeconds;

    public SmsSendResult(@Nullable String bizId, @Nullable String requestId, int cooldownSeconds, int expireSeconds) {
        this.bizId = bizId;
        this.requestId = requestId;
        this.cooldownSeconds = cooldownSeconds;
        this.expireSeconds = expireSeconds;
    }

    /** 阿里云发送回执 ID（排查用） */
    @Nullable
    public String getBizId() {
        return bizId;
    }

    /** 阿里云 RequestId（排查用） */
    @Nullable
    public String getRequestId() {
        return requestId;
    }

    /** 再次发送的间隔秒数，60 */
    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    /** 有效期秒数，300 */
    public int getExpireSeconds() {
        return expireSeconds;
    }
}
