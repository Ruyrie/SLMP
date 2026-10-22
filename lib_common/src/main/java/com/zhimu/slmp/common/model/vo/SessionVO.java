package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 当前会话（API-A08，冷启动与强制步骤分发）。
 */
public class SessionVO {

    @Nullable
    private AccountUserVO user;
    @Nullable
    private String nextStep;
    @Nullable
    private String nextStepText;
    @Nullable
    private RecoveryInfoVO recoveryInfo;
    @Nullable
    private Long lastFarmId;

    @Nullable
    public AccountUserVO getUser() {
        return user;
    }

    @Nullable
    public String getNextStep() {
        return nextStep;
    }

    @Nullable
    public String getNextStepText() {
        return nextStepText;
    }

    @Nullable
    public RecoveryInfoVO getRecoveryInfo() {
        return recoveryInfo;
    }

    @Nullable
    public Long getLastFarmId() {
        return lastFarmId;
    }
}
