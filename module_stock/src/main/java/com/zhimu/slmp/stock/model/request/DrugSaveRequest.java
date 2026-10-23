package com.zhimu.slmp.stock.model.request;

import java.math.BigDecimal;

/** API-M37 / M38 药品档案（修改时 category、catalog_id 不传）；字段直接公开，由 Gson 按下划线命名读写。 */
public class DrugSaveRequest {
    public String category;
    public Long catalogId;
    public String spec;
    public String unit;
    public String packUnit;
    public BigDecimal packSize;
    public BigDecimal minStock;
    public String minStockUnit;
    public Integer version;
}
