package com.zhimu.slmp.account.model.request;

import androidx.annotation.NonNull;

/**
 * API-A02 发送验证码到本人手机号：scene 为 REGENERATE_RECOVERY_CODE 或 CANCEL_ACCOUNT。
 */
public class AccountSmsCodeRequest {

    public static final String SCENE_REGENERATE_RECOVERY_CODE = "REGENERATE_RECOVERY_CODE";
    public static final String SCENE_CANCEL_ACCOUNT = "CANCEL_ACCOUNT";

    private final String scene;

    public AccountSmsCodeRequest(@NonNull String scene) {
        this.scene = scene;
    }

    public String getScene() {
        return scene;
    }
}
