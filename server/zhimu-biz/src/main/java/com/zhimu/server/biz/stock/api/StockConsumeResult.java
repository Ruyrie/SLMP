package com.zhimu.server.biz.stock.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存扣减结果（模块06 / 08 保存记录后回填均价、金额、结存与批次分摊）。
 *
 * @param flowId        写入的流水 ID
 * @param unitPrice     扣减时的均价（元/记账单位）
 * @param amount        扣减金额（元）
 * @param stockQtyAfter 扣减后的结存（饲料为该饲料结存，药品为该批号结存）
 * @param lotNo         药品批号；饲料为 null
 * @param allocations   批次成本分摊（无批次 / 空栏时为空列表）
 */
public record StockConsumeResult(Long flowId, BigDecimal unitPrice, BigDecimal amount,
                                 BigDecimal stockQtyAfter, String lotNo,
                                 List<StockCostAllocation> allocations) {

    public StockConsumeResult {
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
    }
}
