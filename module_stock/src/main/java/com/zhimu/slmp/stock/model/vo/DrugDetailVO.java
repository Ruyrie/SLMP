package com.zhimu.slmp.stock.model.vo;

import java.math.BigDecimal;
import java.util.List;

/** API-M36 药品档案详情（M09）；字段直接公开，由 Gson 按下划线命名读写。 */
public class DrugDetailVO {
    public long drugId;
    public String category;
    public Long catalogId;
    public String name;
    public String catalogSourceText;
    public String spec;
    public String unit;
    public String packUnit;
    public List<String> packUnitOptions;
    public BigDecimal packSize;
    public boolean packSizeLocked;
    public BigDecimal minStock;
    public QtyVO usable;
    public boolean unitLocked;
    public boolean banned;
    public Integer nearDays;
    public Integer version;
    public String updatedAt;
    public String updatedByName;
}
