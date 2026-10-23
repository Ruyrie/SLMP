package com.zhimu.slmp.stock.model.request;

import java.math.BigDecimal;

/** API-M18 登记报损（报损日期固定今天）；字段直接公开，由 Gson 按下划线命名读写。 */
public class FeedLossRequest {
    public Long feedId;
    public BigDecimal inputQty;
    public String inputUnit;
    public String reason;
    public String remark;
    public Integer stockVersion;
}
