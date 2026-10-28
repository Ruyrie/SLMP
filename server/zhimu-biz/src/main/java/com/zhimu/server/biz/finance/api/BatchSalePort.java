package com.zhimu.server.biz.finance.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 出栏扣减/回滚批次存栏与栏舍存栏：由 {@link com.zhimu.server.biz.finance.support.BatchSaleAdapter} 转调模块04 实现，
 * SaleOrderService 在创建/修改/作废出栏单时调用；{@link FinancePortConfig} 的 no-op 只在模块04 缺失时兜底。
 */
public interface BatchSalePort {

    /** 批次各栏舍当前在栏数（t_batch_pen，用于多栏舍选择与校验）；返回空列表时调用方用批次存栏兜底 */
    List<PenStock> penStocks(Long batchId);

    /**
     * 按各栏舍数量原子扣减批次与栏舍存栏、写 SALE 流水；批次扣减后为 0 时结束批次。
     *
     * @return 扣减后的批次存栏；返回 -1 表示端口未实现（调用方按批次当前存栏自行推算）
     */
    int saleOut(Long farmId, Long batchId, LocalDate saleDate, Map<Long, Integer> penQuantities, Long saleOrderId);

    /** 作废出栏单时恢复原栏舍存栏；批次已结束且恢复后大于 0 时恢复在养 */
    int restoreSale(Long farmId, Long batchId, LocalDate saleDate, Map<Long, Integer> penQuantities, Long saleOrderId);

    /** 修改出栏数量时按差额调整（正数扣减、负数恢复） */
    int adjustSale(Long farmId, Long batchId, LocalDate saleDate, Map<Long, Integer> penQuantities, Long saleOrderId);

    /**
     * 栏舍在栏数。
     *
     * @param penId 栏舍 ID
     * @param penName 栏舍名称
     * @param stockCount 该批次在该栏舍的在栏数
     */
    record PenStock(Long penId, String penName, int stockCount) {
    }
}
