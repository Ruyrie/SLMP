package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;
import java.math.BigDecimal;

/**
 * API-D22 登记用药（Idempotency-Key 写入每条记录 client_request_id）：按类型传 batch_id 或 pen_id；员工不传 executor_id。
 */
public class MedicationCreateRequest {

    @Nullable
    public String targetType;
    @Nullable
    public Long batchId;
    @Nullable
    public Long penId;
    public long drugId;
    public long drugLotId;
    @Nullable
    public BigDecimal dose;
    @Nullable
    public BigDecimal usedQty;
    public int courseDays;
    @Nullable
    public String firstDate;
    @Nullable
    public String overDoseNote;
    @Nullable
    public String usageNote;
    @Nullable
    public String reason;
    @Nullable
    public Long executorId;
    @Nullable
    public Long reportId;
    @Nullable
    public Long diagnosisId;
}
