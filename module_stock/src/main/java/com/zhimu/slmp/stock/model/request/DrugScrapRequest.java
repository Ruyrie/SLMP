package com.zhimu.slmp.stock.model.request;

import java.math.BigDecimal;

/** API-M31 登记报废；字段直接公开，由 Gson 按下划线命名读写。 */
public class DrugScrapRequest {
    public Long drugLotId;
    public BigDecimal inputQty;
    public String inputUnit;
    public String reason;
    public String remark;
    public String scrapDate;
    public Integer stockVersion;
}
