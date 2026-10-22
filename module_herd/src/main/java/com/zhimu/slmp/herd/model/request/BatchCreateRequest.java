package com.zhimu.slmp.herd.model.request;

import java.math.BigDecimal;

/**
 * API-L18 批次入栏（L08）。可空字段为 null 时不序列化；capacity_confirmed 只在勾选超容量确认后为 true。
 */
public class BatchCreateRequest {

    public long penId;
    public long breedId;
    public String purpose;
    public String source;
    public String entryDate;
    public int entryAgeDays;
    public int entryCount;
    public BigDecimal entryAvgWeight;
    /** 外购必填，自繁不传 */
    public BigDecimal introUnitPrice;
    public Boolean capacityConfirmed;
}
