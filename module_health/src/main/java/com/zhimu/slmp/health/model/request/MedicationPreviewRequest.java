package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;
import java.math.BigDecimal;

/**
 * API-D21 预估可出栏日期与预计用量（修改时带 exclude_record_id）。
 */
public class MedicationPreviewRequest {

    @Nullable
    public String targetType;
    @Nullable
    public Long batchId;
    @Nullable
    public Long penId;
    @Nullable
    public Long drugId;
    @Nullable
    public String firstDate;
    @Nullable
    public Integer courseDays;
    @Nullable
    public BigDecimal dose;
    @Nullable
    public Long excludeRecordId;
}
