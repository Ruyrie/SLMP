package com.zhimu.server.biz.finance.api;

import java.math.BigDecimal;

/**
 * 批次成本（模块09 后续实现；本模块先 no-op 返回 0）。
 * 饲料、兽药疫苗取 t_batch_material_cost；死淘成本暂未进入核算口径，保留字段供后续扩展。
 */
public interface StockCostPort {

    /** 该批次截至当前的成本；无实现时返回全 0 */
    BatchCost costOf(Long batchId);

    /**
     * 批次成本。
     *
     * @param feed 饲料金额
     * @param feedKg 饲料用量（千克）
     * @param drug 兽药疫苗金额
     * @param death 死淘成本（当前核算口径不使用）
     */
    record BatchCost(BigDecimal feed, BigDecimal feedKg, BigDecimal drug, BigDecimal death) {

        public static final BatchCost ZERO = new BatchCost(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}
