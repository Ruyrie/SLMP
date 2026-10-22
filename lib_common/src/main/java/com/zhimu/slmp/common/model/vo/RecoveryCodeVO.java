package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 恢复码（只显示一次，离开页面后从内存清除，不写日志）。
 */
public class RecoveryCodeVO {

    private long recoveryCodeId;
    @Nullable
    private String code;
    @Nullable
    private String generatedAt;
    @Nullable
    private String phoneMasked;
    /** REGISTER / RECOVERY / REGENERATE */
    @Nullable
    private String scene;
    @Nullable
    private String sceneText;
    private boolean isRegenerated;

    public long getRecoveryCodeId() {
        return recoveryCodeId;
    }

    @Nullable
    public String getCode() {
        return code;
    }

    @Nullable
    public String getGeneratedAt() {
        return generatedAt;
    }

    @Nullable
    public String getPhoneMasked() {
        return phoneMasked;
    }

    @Nullable
    public String getScene() {
        return scene;
    }

    @Nullable
    public String getSceneText() {
        return sceneText;
    }

    public boolean isRegenerated() {
        return isRegenerated;
    }
}
