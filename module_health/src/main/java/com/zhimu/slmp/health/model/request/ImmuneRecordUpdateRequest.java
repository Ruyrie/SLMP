package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;
import java.math.BigDecimal;

/**
 * API-D16 修改接种记录（规则同 API-D14）。
 */
public class ImmuneRecordUpdateRequest {

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
    public int version;
}
