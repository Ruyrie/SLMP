package com.zhimu.slmp.common.model.vo;

import java.math.BigDecimal;

/** 数量（记账单位 value + unit；按包装显示时 pack_unit、pack_size、pack_count、rest_value），模块09 的 StockQtyFormatter 拼写；API-M27 等接口共用；字段直接公开，由 Gson 按下划线命名读写。 */
public class QtyVO {
    public BigDecimal value;
    public String unit;
    public String unitText;
    public String packUnit;
    public BigDecimal packSize;
    public Integer packCount;
    public BigDecimal restValue;
}
