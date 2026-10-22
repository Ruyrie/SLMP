package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;
import java.math.BigDecimal;

/**
 * API-D14 登记接种 / 补免（Idempotency-Key 写入 client_request_id）；员工不传 executor_id。
 */
public class ImmuneRecordCreateRequest {

    public long planId;
    public long drugId;
    public long drugLotId;
    @Nullable
    public BigDecimal dose;
    public int quantity;
    @Nullable
    public String methodCode;
    @Nullable
    public String vaccinateDate;
    @Nullable
    public Long executorId;
}
