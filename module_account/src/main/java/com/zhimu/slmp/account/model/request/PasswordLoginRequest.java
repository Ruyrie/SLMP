package com.zhimu.slmp.account.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-A04 密码登录（正常密码或临时密码）。
 */
public class PasswordLoginRequest {

    private final String phone;
    private final String password;
    private final String deviceId;
    @Nullable
    private final String deviceModel;
    @Nullable
    private final String osVersion;

    public PasswordLoginRequest(@NonNull String phone, @NonNull String password, @NonNull DeviceInfo device) {
        this.phone = phone;
        this.password = password;
        this.deviceId = device.getDeviceId();
        this.deviceModel = device.getDeviceModel();
        this.osVersion = device.getOsVersion();
    }

    public String getPhone() {
        return phone;
    }

    public String getPassword() {
        return password;
    }

    public String getDeviceId() {
        return deviceId;
    }

    @Nullable
    public String getDeviceModel() {
        return deviceModel;
    }

    @Nullable
    public String getOsVersion() {
        return osVersion;
    }
}
