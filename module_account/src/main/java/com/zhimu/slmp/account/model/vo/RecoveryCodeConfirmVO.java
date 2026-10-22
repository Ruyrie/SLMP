package com.zhimu.slmp.account.model.vo;

import androidx.annotation.Nullable;

/**
 * API-A14 确认恢复码已保存的结果。
 */
public class RecoveryCodeConfirmVO {

    private long recoveryCodeId;
    /** ACTIVE */
    @Nullable
    private String status;
    @Nullable
    private String confirmedAt;
    /** 确认后的 next_step（通常为 NONE） */
    @Nullable
    private String nextStep;

    public long getRecoveryCodeId() {
        return recoveryCodeId;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getConfirmedAt() {
        return confirmedAt;
    }

    @Nullable
    public String getNextStep() {
        return nextStep;
    }
}
