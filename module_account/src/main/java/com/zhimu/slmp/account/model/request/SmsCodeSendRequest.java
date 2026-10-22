package com.zhimu.slmp.account.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-A01 发送验证码（未登录）：phone、scene，RECOVERY_NEW_PHONE 时带 old_phone。
 */
public class SmsCodeSendRequest {

    public static final String SCENE_LOGIN = "LOGIN";
    public static final String SCENE_REGISTER = "REGISTER";
    public static final String SCENE_RESET_PASSWORD = "RESET_PASSWORD";
    public static final String SCENE_RECOVERY_NEW_PHONE = "RECOVERY_NEW_PHONE";

    private final String phone;
    private final String scene;
    @Nullable
    private final String oldPhone;

    public SmsCodeSendRequest(@NonNull String phone, @NonNull String scene, @Nullable String oldPhone) {
        this.phone = phone;
        this.scene = scene;
        this.oldPhone = oldPhone;
    }

    public SmsCodeSendRequest(@NonNull String phone, @NonNull String scene) {
        this(phone, scene, null);
    }

    public String getPhone() {
        return phone;
    }

    public String getScene() {
        return scene;
    }

    @Nullable
    public String getOldPhone() {
        return oldPhone;
    }
}
