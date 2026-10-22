package com.zhimu.slmp.account.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-A03 注册。
 */
public class RegisterRequest {

    private final String phone;
    private final String smsCode;
    private final String password;
    private final boolean agreeTerms;
    private final String agreementVersion;
    private final String deviceId;
    @Nullable
    private final String deviceModel;
    @Nullable
    private final String osVersion;

    public RegisterRequest(@NonNull String phone, @NonNull String smsCode, @NonNull String password,
                           boolean agreeTerms, @NonNull String agreementVersion, @NonNull DeviceInfo device) {
        this.phone = phone;
        this.smsCode = smsCode;
        this.password = password;
        this.agreeTerms = agreeTerms;
        this.agreementVersion = agreementVersion;
        this.deviceId = device.getDeviceId();
        this.deviceModel = device.getDeviceModel();
        this.osVersion = device.getOsVersion();
    }

    public String getPhone() {
        return phone;
    }

    public String getSmsCode() {
        return smsCode;
    }

    public String getPassword() {
        return password;
    }

    public boolean isAgreeTerms() {
        return agreeTerms;
    }

    public String getAgreementVersion() {
        return agreementVersion;
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
