package com.zhimu.slmp.stock.model.request;

import java.math.BigDecimal;

/** 实际到账退款；服务端以原退货单关联收入，不影响库存。 */
public class ReturnRefundRequest {
    public BigDecimal amount;
    public String refundDate;
    public String remark;
    public Integer version;
}
