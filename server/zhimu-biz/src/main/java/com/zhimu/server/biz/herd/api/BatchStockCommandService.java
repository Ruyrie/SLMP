package com.zhimu.server.biz.herd.api;

import com.zhimu.server.common.enums.BizType;

import java.time.LocalDate;
import java.util.List;

/**
 * 跨模块出栏存栏端口（模块10 出栏单调用）：按批次 / 栏舍扣减存栏，出栏单作废时回滚。
 * <p>
 * 只暴露模块04 自己的 record 类型，调用方不需要依赖 herd 的实体或 Mapper。
 * 一次调用对应一个批次；同一出栏单可多次调用（不同批次）。幂等键为（来源业务类型、来源业务 ID、批次），
 * 重复调用不会重复扣减；{@link #restoreSale(BizType, Long)} 按来源业务 ID 回滚全部批次的出栏。
 */
public interface BatchStockCommandService {

    /** 出栏扣减：批次 current_count 减、sold_count 加，批次栏舍与栏舍在栏数同步减；批次减到 0 时自动结束（ALL_SOLD） */
    BatchSaleResult sale(SaleStockCommand command);

    /** 出栏作废：按来源业务（通常 SALE_ORDER + 出栏单 ID）回滚存栏与计数，幂等 */
    void restoreSale(BizType bizType, Long bizId);

    /** 出栏作废（固定 SALE_ORDER） */
    default void restoreSale(Long saleOrderId) {
        restoreSale(BizType.SALE_ORDER, saleOrderId);
    }

    /**
     * 出栏扣减命令。
     *
     * @param batchId       批次 ID
     * @param bizType       来源业务类型，通常 SALE_ORDER
     * @param bizId         来源业务 ID（出栏单 ID），与批次一起作为幂等键
     * @param saleDate      出栏日期（不能晚于今天、不能早于入栏日期）
     * @param quantity      出栏总数；items 为空时按此数量在批次各栏舍自动分摊
     * @param items         按栏舍分摊的出栏数量；为空时按 quantity 自动分摊
     * @param operatorUserId 操作人（写入流水的执行人 / 时间线登记人），为空取当前登录用户
     */
    record SaleStockCommand(Long batchId, BizType bizType, Long bizId, LocalDate saleDate, Integer quantity,
                            List<SaleStockItem> items, Long operatorUserId) {

        /** 出栏单便捷构造：来源固定 SALE_ORDER */
        public SaleStockCommand(Long batchId, Long saleOrderId, LocalDate saleDate, Integer quantity,
                               List<SaleStockItem> items, Long operatorUserId) {
            this(batchId, BizType.SALE_ORDER, saleOrderId, saleDate, quantity, items, operatorUserId);
        }

        /** 按栏舍明细出栏：总数取明细之和 */
        public SaleStockCommand(Long batchId, Long saleOrderId, LocalDate saleDate,
                               List<SaleStockItem> items, Long operatorUserId) {
            this(batchId, BizType.SALE_ORDER, saleOrderId, saleDate, sum(items), items, operatorUserId);
        }

        private static Integer sum(List<SaleStockItem> items) {
            if (items == null || items.isEmpty()) {
                return null;
            }
            int total = 0;
            for (SaleStockItem item : items) {
                if (item != null) {
                    total += item.quantity();
                }
            }
            return total;
        }
    }

    /** 单个栏舍的出栏数量 */
    record SaleStockItem(Long penId, int quantity) {
    }

    /** 出栏后的批次存栏与分摊结果 */
    record BatchSaleResult(Long batchId, String batchStatus, int quantity, int currentCount, int soldCount,
                           boolean batchEnded, List<Long> eventIds, List<BatchPenStock> pens) {
    }

    /** 某栏舍本次出栏数量与出栏后在栏数 */
    record BatchPenStock(Long penId, String penName, int quantity, int stockCount) {
    }
}
