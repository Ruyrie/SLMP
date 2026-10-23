package com.zhimu.slmp.stock.model.vo;

import java.math.BigDecimal;

/** API-M04 饲料档案（M02）；字段直接公开，由 Gson 按下划线命名读写。 */
public class FeedDetailVO {
    public long feedId;
    public String name;
    public String feedTypeCode;
    public String feedTypeText;
    public String spec;
    public String unit;
    public String unitText;
    public String packUnit;
    public BigDecimal packSize;
    public Long categoryId;
    public String categoryText;
    public BigDecimal minStock;
    public Integer warnDays;
    public boolean unitLocked;
    public Integer editVersion;
    public String updatedAt;
    public String updatedByName;
}
