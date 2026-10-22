package com.zhimu.slmp.account.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-A05 验证码登录（密码锁定期间照常可用）。
 */
public class SmsLoginRequest {

    private final String phone;
    private final String smsCode;
    private final String deviceId;
    @Nullable
    private final String deviceModel;
    @Nullable
    private final String osVersion;

    public SmsLoginRequest(@NonNull String phone, @NonNull String smsCode, @NonNull DeviceInfo device) {
        this(phone, smsCode, device.getDeviceId(), device.getDeviceModel(), device.getOsVersion());
    }

    private SmsLoginRequest(@NonNull String phone, @NonNull String smsCode, String deviceId,
                            @Nullable String deviceModel, @Nullable String osVersion) {
        this.phone = phone;
        this.smsCode = smsCode;
        this.deviceId = deviceId;
        this.deviceModel = deviceModel;
        this.osVersion = osVersion;
    }

    /** 换成另一个验证码的副本（测试直连校验通过后提交给 Mock 用），相同时返回自身 */
    @NonNull
    public SmsLoginRequest withSmsCode(@NonNull String code) {
        return code.equals(smsCode) ? this : new SmsLoginRequest(phone, code, deviceId, deviceModel, osVersion);
    }

    public String getPhone() {
        return phone;
    }

    public String getSmsCode() {
        return smsCode;
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
