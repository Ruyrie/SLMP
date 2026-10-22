package com.zhimu.slmp.feed.model.request;

import java.math.BigDecimal;
import java.util.List;

/** API-G22 修改称重记录：称重日期不可改，存栏上限取记录的 stock_count；version 必填。 */
public class WeighRecordUpdateRequest {

    public String mode;
    public Integer sampleCount;
    public BigDecimal totalWeight;
    public List<WeighItemRequest> items;
    public int version;
}
