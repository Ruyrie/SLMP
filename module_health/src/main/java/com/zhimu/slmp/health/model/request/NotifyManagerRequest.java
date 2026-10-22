package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;

/**
 * API-D27 员工通知场长：action 为 DELETE / UPDATE。
 */
public class NotifyManagerRequest {

    @Nullable
    public String action;

    public NotifyManagerRequest() {
    }

    public NotifyManagerRequest(@Nullable String action) {
        this.action = action;
    }
}
