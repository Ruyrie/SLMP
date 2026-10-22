package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;

/**
 * API-D38 登记消毒（Idempotency-Key 写入 client_request_id）：计划任务传 task_id，临时消毒传 pen_id；员工不传 executor_id。
 */
public class DisinfectRecordCreateRequest {

    @Nullable
    public String recordType;
    @Nullable
    public Long taskId;
    @Nullable
    public Long penId;
    @Nullable
    public String actualTime;
    @Nullable
    public Long executorId;
    public long drugId;
    public long drugLotId;
    @Nullable
    public BigDecimal usageQty;
    @Nullable
    public String concentration;
    @Nullable
    public String methodCode;
    @Nullable
    public Boolean concentrationConfirmed;
    @Nullable
    public List<Long> fileIds;
}
