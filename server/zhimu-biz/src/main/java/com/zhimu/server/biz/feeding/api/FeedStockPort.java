package com.zhimu.server.biz.feeding.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 饲料库存端口（模块06 使用，由 {@code StockFeedStockAdapter} 转调模块09 的
 * {@link com.zhimu.server.biz.stock.api.FeedStockPort}）。
 * 模块06 只在投喂记录保存 / 修改 / 作废时调用扣减与回滚，不直接写 t_feed / t_stock_flow。
 */
public interface FeedStockPort {

    /** 饲料简要信息与可用结存；不存在返回 null */
    FeedBrief getFeed(Long farmId, Long feedId);

    /** 本场可选饲料，按饲料 ID 排序 */
    List<FeedBrief> listFeeds(Long farmId);

    /**
     * 投喂扣减库存，返回库存流水 ID；结存不足返回 {@link DeductResult#insufficient}。
     * 实现加入调用方事务（默认传播）：投喂记录保存失败时扣减一并回滚。
     */
    DeductResult deductForFeeding(DeductCommand command);

    /**
     * 按投喂记录回滚库存（修改退回或作废）：找到该记录对应的有效流水后冲回结存，
     * 没有有效流水时静默返回（幂等）。no-op 实现不做任何事。
     *
     * @param feedRecordId 投喂记录 ID（t_feed_record.id，即库存流水的 biz_id）
     */
    void rollbackForFeeding(Long feedRecordId);

    /** 饲料简要信息 */
    record FeedBrief(Long feedId, String feedName, String unit, BigDecimal stockQty, Integer remainingDays,
                     boolean lowStock, boolean selectable, String disabledReason) {
    }

    /** 扣减命令 */
    record DeductCommand(Long farmId, Long feedId, BigDecimal quantity, Long batchId, Long penId,
                         Long feedRecordId, LocalDate bizDate, Long operatorId) {
    }

    /** 扣减结果：成功带流水 ID，不足带当前结存 */
    record DeductResult(boolean success, Long stockFlowId, BigDecimal stockQty) {
        public static DeductResult ok(Long stockFlowId) {
            return new DeductResult(true, stockFlowId, null);
        }

        public static DeductResult insufficient(BigDecimal stockQty) {
            return new DeductResult(false, null, stockQty);
        }

        public boolean insufficient() {
            return !success && stockQty != null;
        }
    }
}
