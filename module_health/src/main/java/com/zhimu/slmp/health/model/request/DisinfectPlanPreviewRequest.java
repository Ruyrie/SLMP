package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;
import java.util.List;

/**
 * API-D30 任务数与提醒预估（修改时带 plan_id）。
 */
public class DisinfectPlanPreviewRequest {

    @Nullable
    public List<Long> penIds;
    @Nullable
    public Integer cycleDays;
    @Nullable
    public String startDate;
    @Nullable
    public String endDate;
    @Nullable
    public Long planId;
}
