package com.zhimu.slmp.feed.model.request;

import java.math.BigDecimal;

/**
 * API-G06 修改当天投喂记录（所有者、场长）：同一饲料按“新实投 − 原实投”校验结存，换饲料按新饲料结存；version 必填。
 */
public class FeedRecordUpdateRequest {

    public long feedId;
    public BigDecimal actualQty;
    public Long executorId;
    public String remark;
    public int version;
}
