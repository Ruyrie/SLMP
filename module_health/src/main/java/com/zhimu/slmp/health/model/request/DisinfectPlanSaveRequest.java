package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;
import java.util.List;

/**
 * API-D31 新建并启用（Idempotency-Key）、API-D32 修改（version 必填）；due_time 新建不传（默认 17:00），修改带回原值。
 */
public class DisinfectPlanSaveRequest {

    @Nullable
    public List<Long> penIds;
    public int cycleDays;
    @Nullable
    public String startDate;
    @Nullable
    public String endDate;
    public long catalogId;
    @Nullable
    public String concentration;
    @Nullable
    public String methodCode;
    @Nullable
    public String dueTime;
    @Nullable
    public Integer version;
}
