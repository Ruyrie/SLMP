package com.zhimu.slmp.health.model.request;

import androidx.annotation.Nullable;
import java.math.BigDecimal;

/**
 * API-D24 修改用药记录：用药对象和批次不可改。
 */
public class MedicationUpdateRequest {

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
    public int version;
}
