package com.zhimu.server.biz.stock.api;

import java.math.BigDecimal;

/**
 * 批次物资成本汇总（模块10 结算读取）。
 * <p>
 * 四个分项与总计，单位元，保留到分：feedCost 饲料成本、drugCost 药品成本、introCost 入栏成本、
 * otherCost 其他成本；total 为四者之和（即该批次全部 NORMAL 成本明细金额合计）。
 * 没有成本明细时各项与 total 均为 0.00。
 *
 * @param feedCost  饲料成本（cost_type = FEED）
 * @param drugCost  药品成本（cost_type = DRUG）
 * @param introCost 入栏成本（cost_type = INTRO）
 * @param otherCost 其他成本（未识别的 cost_type）
 * @param total     合计
 */
public record BatchCost(BigDecimal feedCost, BigDecimal drugCost, BigDecimal introCost,
                        BigDecimal otherCost, BigDecimal total) {
}
