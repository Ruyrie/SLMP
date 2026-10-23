package com.zhimu.server.biz.feeding.support;

import com.zhimu.server.biz.feeding.api.FeedStockPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 模块09 缺失时的兜底实现（模块09 已合并时由 {@link StockFeedStockAdapter}（@Primary）接管）：
 * 只提供与 App Mock 对齐的示例饲料（4001 育肥全价料、4002 保育料、4003 乳猪教槽料），
 * 扣减只按示例结存判断是否够用、不写 t_stock_flow，回滚不做任何事。
 * <p>
 * 真实实现见 {@link StockFeedStockAdapter}：读模块09 的 t_feed，扣减 / 回滚按投喂记录写库存流水。
 */
@Service
public class NoopFeedStockPort implements FeedStockPort {

    private static final Map<Long, FeedBrief> FEEDS = Map.of(
            4001L, new FeedBrief(4001L, "育肥全价料", "KG", new BigDecimal("500.0"), 1, true, true, null),
            4002L, new FeedBrief(4002L, "保育料", "KG", new BigDecimal("1200.0"), 5, false, true, null),
            4003L, new FeedBrief(4003L, "乳猪教槽料", "KG", BigDecimal.ZERO, null, false, false, "结存 0 千克，不能出库"));

    @Override
    public FeedBrief getFeed(Long farmId, Long feedId) {
        return feedId == null ? null : FEEDS.get(feedId);
    }

    @Override
    public List<FeedBrief> listFeeds(Long farmId) {
        return List.of(FEEDS.get(4001L), FEEDS.get(4002L), FEEDS.get(4003L));
    }

    @Override
    public DeductResult deductForFeeding(DeductCommand command) {
        FeedBrief feed = getFeed(command == null ? null : command.farmId(),
                command == null ? null : command.feedId());
        if (feed != null && command.quantity() != null && feed.stockQty().compareTo(command.quantity()) < 0) {
            return DeductResult.insufficient(feed.stockQty());
        }
        return DeductResult.ok(null);
    }

    @Override
    public void rollbackForFeeding(Long feedRecordId) {
        // 兜底实现没有库存流水可回滚
    }
}
