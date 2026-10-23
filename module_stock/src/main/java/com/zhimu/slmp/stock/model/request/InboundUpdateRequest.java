package com.zhimu.slmp.stock.model.request;

import java.math.BigDecimal;

/** API-M10 修改未出库入库单：饲料带生产日期、保质期、供应商，药品带有效期；字段直接公开，由 Gson 按下划线命名读写。 */
public class InboundUpdateRequest {
    public BigDecimal inputQty;
    public String inputUnit;
    public BigDecimal unitPrice;
    public String inboundDate;
    public Boolean syncFinance;
    public String productionDate;
    public String shelfLifeDate;
    public String supplierName;
    public String expireDate;
    public Integer version;
}
