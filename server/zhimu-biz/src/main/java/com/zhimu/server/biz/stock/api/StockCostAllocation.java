package com.zhimu.server.biz.stock.api;

import java.math.BigDecimal;

/**
 * 批次成本分摊行（模块09 写 t_batch_material_cost，模块10 结算读取；模块08 可用于展示分摊）。
 *
 * @param batchId   批次 ID
 * @param batchNo   批次号，例如 ZM2610-031
 * @param headCount 分摊时的批次在栏数
 * @param ratio     分摊比例，4 位小数
 * @param amount    分摊金额（元）
 */
public record StockCostAllocation(Long batchId, String batchNo, Integer headCount, BigDecimal ratio,
                                  BigDecimal amount) {
}
