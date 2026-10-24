package com.zhimu.server.admin.base.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 按单位汇总的库存数量（ADM-D31）。 */
@Getter
@Setter
public class StockTotalRow {

    private BigDecimal qty;
    private String unit;
}
