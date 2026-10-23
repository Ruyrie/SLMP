package com.zhimu.slmp.stock.model.request;

import java.math.BigDecimal;

/** API-M30 兽药疫苗入库（input_unit PACK）；字段直接公开，由 Gson 按下划线命名读写。 */
public class DrugInboundRequest {
    public Long drugId;
    public String lotNo;
    public String inboundDate;
    public String expireDate;
    public BigDecimal inputQty;
    public String inputUnit;
    public BigDecimal unitPrice;
    public Boolean syncFinance;
}
