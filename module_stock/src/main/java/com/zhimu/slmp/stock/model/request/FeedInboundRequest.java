package com.zhimu.slmp.stock.model.request;

import java.math.BigDecimal;

/** API-M08 饲料入库（input_unit 固定 MEASURE）；字段直接公开，由 Gson 按下划线命名读写。 */
public class FeedInboundRequest {
    public Long feedId;
    public BigDecimal inputQty;
    public String inputUnit;
    public BigDecimal unitPrice;
    public String productionDate;
    public String shelfLifeDate;
    public String supplierName;
    public String inboundDate;
    public Boolean syncFinance;
}
