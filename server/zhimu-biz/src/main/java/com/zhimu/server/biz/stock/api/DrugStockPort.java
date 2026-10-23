package com.zhimu.server.biz.stock.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * 药品库存端口：模块08 接种 / 用药 / 消毒记录保存、作废时扣减与回滚药品库存，并提供可选批号。
 * <p>
 * 实现方（模块09）加入调用方事务（默认传播）；结存不足抛 42201，批号过期 / 停用抛 42204，
 * 药品列为禁用药抛 42203。
 */
public interface DrugStockPort {

    /**
     * 可选批号（含不可选与原因），对应 API-M27。
     *
     * @param purpose USE 领用 / RETURN 退货 / VACCINATE 接种 / MEDICATE 用药 / DISINFECT 消毒
     * @param needQty 需要数量（记账单位），可空；用于标记 insufficient
     */
    List<DrugLotOption> lotsFor(Long farmId, Long drugId, String purpose, BigDecimal needQty);

    /** 扣减批号结存并写流水（计入批次成本），drugLotId 为空时按先到期先出选批号 */
    StockConsumeResult deduct(DrugStockCommand command);

    /** 作废来源记录对应的流水并回滚结存；找不到有效流水时静默返回（幂等） */
    void restore(String bizType, Long bizId);
}
