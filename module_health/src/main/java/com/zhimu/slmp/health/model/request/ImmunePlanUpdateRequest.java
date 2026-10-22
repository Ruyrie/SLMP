package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;
import java.math.BigDecimal;

/**
 * API-D08 修改剂量、方式（疫苗与计划时间不可改，改期用 API-D06）。
 */
public class ImmunePlanUpdateRequest {

    @Nullable
    public BigDecimal dose;
    @Nullable
    public String methodCode;
    @Nullable
    public String dueTime;
    public int version;
}
