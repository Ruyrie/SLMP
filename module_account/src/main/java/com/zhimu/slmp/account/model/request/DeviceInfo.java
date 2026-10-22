package com.zhimu.slmp.account.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 登录类请求共用的设备信息（DeviceInfoProvider 生成）：device_id（安装标识，≤64）、device_model（≤50）、os_version（≤30）。
 */
public final class DeviceInfo {

    @NonNull
    private final String deviceId;
    @Nullable
    private final String deviceModel;
    @Nullable
    private final String osVersion;

    public DeviceInfo(@NonNull String deviceId, @Nullable String deviceModel, @Nullable String osVersion) {
        this.deviceId = deviceId;
        this.deviceModel = deviceModel;
        this.osVersion = osVersion;
    }

    @NonNull
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
