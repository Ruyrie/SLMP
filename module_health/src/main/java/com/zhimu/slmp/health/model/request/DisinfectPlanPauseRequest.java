package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;

/**
 * API-D33 暂停计划：原因选填，不超过 30 字。
 */
public class DisinfectPlanPauseRequest {

    @Nullable
    public String reason;
    public int version;
}
