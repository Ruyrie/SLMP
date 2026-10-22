package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;
import java.math.BigDecimal;

/**
 * API-D07 添加免疫项目（Idempotency-Key）：按日龄传 plan_age_day，按日期传 plan_date；immune_round、due_time 暂不传（待定事项 5）。
 */
public class ImmunePlanCreateRequest {

    public long drugId;
    @Nullable
    public String planMode;
    @Nullable
    public Integer planAgeDay;
    @Nullable
    public String planDate;
    @Nullable
    public BigDecimal dose;
    @Nullable
    public String methodCode;
    @Nullable
    public String immuneRound;
    @Nullable
    public String dueTime;
}
