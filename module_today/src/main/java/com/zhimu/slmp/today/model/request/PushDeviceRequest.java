package com.zhimu.slmp.today.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 登记推送设备（API-T20）：品牌、型号、系统与 App 版本各截断到 40 字符。
 */
public class PushDeviceRequest {

    public static final String PLATFORM_ANDROID = "ANDROID";
    private static final int MAX_LENGTH = 40;

    @NonNull
    private final String registrationId;
    @NonNull
    private final String platform;
    @Nullable
    private final String deviceBrand;
    @Nullable
    private final String deviceModel;
    @Nullable
    private final String osVersion;
    @Nullable
    private final String appVersion;
    private final boolean notifyPermission;

    public PushDeviceRequest(@NonNull String registrationId, @Nullable String deviceBrand, @Nullable String deviceModel,
                             @Nullable String osVersion, @Nullable String appVersion, boolean notifyPermission) {
        this.registrationId = registrationId;
        this.platform = PLATFORM_ANDROID;
        this.deviceBrand = cut(deviceBrand);
        this.deviceModel = cut(deviceModel);
        this.osVersion = cut(osVersion);
        this.appVersion = cut(appVersion);
        this.notifyPermission = notifyPermission;
    }

    @Nullable
    private static String cut(@Nullable String s) {
        return s == null || s.length() <= MAX_LENGTH ? s : s.substring(0, MAX_LENGTH);
    }

    @NonNull
    public String getRegistrationId() {
        return registrationId;
    }

    @NonNull
    public String getPlatform() {
        return platform;
    }

    @Nullable
    public String getDeviceBrand() {
        return deviceBrand;
    }

    @Nullable
    public String getDeviceModel() {
        return deviceModel;
    }

    @Nullable
    public String getOsVersion() {
        return osVersion;
    }

    @Nullable
    public String getAppVersion() {
        return appVersion;
    }

    public boolean isNotifyPermission() {
        return notifyPermission;
    }

    /** 参数未变不重复上报 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PushDeviceRequest)) {
            return false;
        }
        PushDeviceRequest r = (PushDeviceRequest) o;
        return notifyPermission == r.notifyPermission && registrationId.equals(r.registrationId)
                && Objects.equals(deviceBrand, r.deviceBrand) && Objects.equals(deviceModel, r.deviceModel)
                && Objects.equals(osVersion, r.osVersion) && Objects.equals(appVersion, r.appVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registrationId, deviceBrand, deviceModel, osVersion, appVersion, notifyPermission);
    }
}
