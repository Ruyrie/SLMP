package com.zhimu.slmp.account.model.vo;

import androidx.annotation.Nullable;

/**
 * API-A10 凭恢复码提交找回申请的结果。
 */
public class AccountRecoverySubmitVO {

    private long recoveryId;
    /** 151****2288 */
    @Nullable
    private String newPhoneMasked;
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    @Nullable
    private String submittedAt;

    public long getRecoveryId() {
        return recoveryId;
    }

    @Nullable
    public String getNewPhoneMasked() {
        return newPhoneMasked;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }

    @Nullable
    public String getSubmittedAt() {
        return submittedAt;
    }
}
