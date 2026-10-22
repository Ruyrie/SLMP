package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

/**
 * 推送设备登记结果（API-T20）。
 */
public class PushDeviceVO {

    @Nullable
    private String registrationId;
    @Nullable
    private String jpushAlias;
    @Nullable
    private String status;
    private boolean aliasBound;
    private boolean notifyPermission;

    @Nullable
    public String getRegistrationId() {
        return registrationId;
    }

    @Nullable
    public String getJpushAlias() {
        return jpushAlias;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    public boolean isAliasBound() {
        return aliasBound;
    }

    public boolean isNotifyPermission() {
        return notifyPermission;
    }
}
