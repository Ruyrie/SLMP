package com.zhimu.slmp.stock.model.request;

import java.math.BigDecimal;

/** API-M22 当天修改出库单（出库类型、物资、批号不可改）；字段直接公开，由 Gson 按下划线命名读写。 */
public class OutboundUpdateRequest {
    public BigDecimal inputQty;
    public String inputUnit;
    public String destination;
    public String targetType;
    public Long batchId;
    public Long penId;
    public Long receiverId;
    public String returnTo;
    public String returnReason;
    public String remark;
    public String outDate;
    public Integer version;
}
