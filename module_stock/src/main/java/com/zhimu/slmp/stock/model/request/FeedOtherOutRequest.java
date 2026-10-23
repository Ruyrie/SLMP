package com.zhimu.slmp.stock.model.request;

import java.math.BigDecimal;

/** API-M20 饲料其他出库；字段直接公开，由 Gson 按下划线命名读写。 */
public class FeedOtherOutRequest {
    public Long feedId;
    public BigDecimal inputQty;
    public String inputUnit;
    public String destination;
    public String remark;
    public String outDate;
    public Integer stockVersion;
}
