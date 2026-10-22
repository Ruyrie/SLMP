package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;

/**
 * API-D06 调整计划日期：新日期不早于今天且不等于原日期；原因 1–50 字。
 */
public class ImmunePlanPostponeRequest {

    @Nullable
    public String newDate;
    @Nullable
    public String reason;
    public int version;
}
