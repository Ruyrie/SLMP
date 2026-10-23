package com.zhimu.server.biz.stock.api;

/**
 * 饲料库存端口：模块06 保存 / 作废饲喂记录时扣减与回滚饲料库存。
 * <p>
 * 实现方（模块09）加入调用方事务（默认传播）；结存不足抛 42201（data.current_qty），
 * 养殖场状态、栏舍范围由调用方先行校验。
 */
public interface FeedStockPort {

    /** 扣减饲料结存并写 FEED_USE 流水（is_feed_usage = 1，计入饲料用量与批次成本） */
    StockConsumeResult deduct(FeedStockCommand command);

    /** 作废来源记录对应的流水并回滚结存；找不到有效流水时静默返回（幂等） */
    void restore(String bizType, Long bizId);
}
