package com.zhimu.server.biz.stock.query;

import lombok.Data;

import java.math.BigDecimal;

/** 按 cost_type 汇总的批次成本（t_batch_material_cost，模块10 读取）。 */
@Data
public class CostTypeSum {

    /** 成本类型（COST_TYPE）：FEED / DRUG / INTRO 等 */
    private String costType;
    /** 金额合计（元） */
    private BigDecimal amount;
}
