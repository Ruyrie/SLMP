package com.zhimu.slmp.stock.model.request;

import java.math.BigDecimal;

/** API-M32 领用 / 退货；字段直接公开，由 Gson 按下划线命名读写。 */
public class DrugOutboundRequest {
    public String outType;
    public Long drugId;
    public Long drugLotId;
    public BigDecimal inputQty;
    public String inputUnit;
    public String targetType;
    public Long batchId;
    public Long penId;
    public Long receiverId;
    public String returnTo;
    public String returnReason;
    public String remark;
    public String outDate;
    public Integer stockVersion;
}
