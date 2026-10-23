package com.zhimu.slmp.stock.model.vo;

import java.math.BigDecimal;

/** 批号选项；stock_version 为技术准备补充字段（待定事项 1）；字段直接公开，由 Gson 按下划线命名读写。 */
public class DrugLotOptionVO {
    public long lotId;
    public String lotNo;
    public String expireDate;
    public Integer daysToExpire;
    public String lotStatus;
    public String lotStatusText;
    public QtyVO stock;
    public boolean priorityOut;
    public boolean insufficient;
    public boolean canGoScrap;
    public BigDecimal avgPrice;
    public Integer stockVersion;
}
