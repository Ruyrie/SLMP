package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;

/**
 * API-D40 修改消毒记录（照片整体替换；类型和任务不可改）。
 */
public class DisinfectRecordUpdateRequest {

    @Nullable
    public String actualTime;
    @Nullable
    public Long executorId;
    public long drugLotId;
    @Nullable
    public BigDecimal usageQty;
    @Nullable
    public String concentration;
    @Nullable
    public Boolean concentrationConfirmed;
    @Nullable
    public String methodCode;
    @Nullable
    public List<Long> fileIds;
    public int version;
}
