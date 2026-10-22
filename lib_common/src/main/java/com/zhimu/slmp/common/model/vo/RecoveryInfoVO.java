package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 账号找回信息（next_step 为 RECOVERY_CHANGE_PASSWORD 时返回）。
 */
public class RecoveryInfoVO {

    @Nullable
    private String newPhoneMasked;
    @Nullable
    private String recoveredAt;
    @Nullable
    private String cancelAvailableDate;

    @Nullable
    public String getNewPhoneMasked() {
        return newPhoneMasked;
    }

    @Nullable
    public String getRecoveredAt() {
        return recoveredAt;
    }

    @Nullable
    public String getCancelAvailableDate() {
        return cancelAvailableDate;
    }
}
