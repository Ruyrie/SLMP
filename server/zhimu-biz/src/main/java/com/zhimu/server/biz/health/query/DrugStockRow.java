package com.zhimu.server.biz.health.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 某药品可用批号结存合计（t_drug_lot）：未过期、未停用的批号结存之和。 */
@Getter
@Setter
public class DrugStockRow {
    private Long drugId;
    private BigDecimal stockQty;
}
