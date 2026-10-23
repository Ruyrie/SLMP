package com.zhimu.server.biz.stock.api;

/**
 * 批次成本只读端口（模块10 结算、批次成本页使用；模块09 提供实现）。
 * <p>
 * 数据来自 t_batch_material_cost（库存扣减时随流水写入，作废流水对应明细置 VOID），
 * 只统计 status = NORMAL 的明细；不依赖 finance 包，模块10 侧用适配器接入。
 */
public interface StockCostQueryService {

    /**
     * 汇总某批次的物资成本。
     *
     * @param farmId  养殖场 ID，为空时取当前养殖场上下文
     * @param batchId 批次 ID，为空时返回全 0
     * @return 饲料 / 药品 / 入栏 / 其他与合计，金额单位元、两位小数；无明细时全为 0.00
     */
    BatchCost batchCost(Long farmId, Long batchId);
}
