package com.zhimu.slmp.stock.model.request;

import java.math.BigDecimal;

/** API-M05 / M06 饲料档案；字段直接公开，由 Gson 按下划线命名读写。 */
public class FeedSaveRequest {
    public String name;
    public String feedTypeCode;
    public String spec;
    public String packUnit;
    public BigDecimal packSize;
    public Long categoryId;
    public BigDecimal minStock;
    public Integer warnDays;
    public Integer editVersion;
}
