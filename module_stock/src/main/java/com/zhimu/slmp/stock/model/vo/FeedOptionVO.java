package com.zhimu.slmp.stock.model.vo;

import java.math.BigDecimal;

/** API-M03 饲料下拉（M03、M10）；stock_version 为技术准备补充字段（待定事项 1，服务端未列出时为 null）；字段直接公开，由 Gson 按下划线命名读写。 */
public class FeedOptionVO {
    public long feedId;
    public String name;
    public QtyVO stock;
    public Integer remainingDays;
    public boolean lowStock;
    public boolean selectable;
    public String disabledReason;
    public BigDecimal avgPrice;
    public Integer stockVersion;
}
