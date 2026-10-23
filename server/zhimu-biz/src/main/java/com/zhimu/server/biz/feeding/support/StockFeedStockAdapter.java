package com.zhimu.server.biz.feeding.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.stock.api.FeedStockCommand;
import com.zhimu.server.biz.stock.api.StockConsumeResult;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 饲料库存端口的真实实现：转调模块09 的 {@link com.zhimu.server.biz.stock.api.FeedStockPort}，
 * 读完模块09 的饲料档案与结存（t_feed）。模块06 不直接写 t_feed / t_stock_flow。
 * <p>
 * 扣减按投喂记录记流水（biz_type = FEED_RECORD、biz_id = t_feed_record.id），回滚按同一条业务记录
 * 找到有效流水后冲回；两者都是默认事务传播，与投喂记录的保存 / 修改 / 作废同事务，
 * 记录保存失败时扣减一并回滚。结存不足时模块09 抛 42201，这里换算成
 * {@link DeductResult#insufficient}，由模块06 给出与 App 一致的库存不足提示。
 * <p>
 * 模块09 未合并时本类不加载（{@code @ConditionalOnClass}），由 {@link NoopFeedStockPort} 兜底。
 */
@Service
@Primary
@ConditionalOnClass(name = "com.zhimu.server.biz.stock.api.FeedStockPort")
@RequiredArgsConstructor
public class StockFeedStockAdapter implements FeedStockPort {

    private static final String UNIT_KG = "KG";
    private static final String EMPTY_REASON = "结存 0 千克，不能出库";

    private final com.zhimu.server.biz.stock.api.FeedStockPort stockPort;
    private final FeedMapper feedMapper;

    @Override
    public FeedBrief getFeed(Long farmId, Long feedId) {
        Feed feed = feedId == null ? null : feedMapper.selectById(feedId);
        if (feed == null || (farmId != null && feed.getFarmId() != null && !farmId.equals(feed.getFarmId()))) {
            return null;
        }
        return brief(feed);
    }

    @Override
    public List<FeedBrief> listFeeds(Long farmId) {
        List<Feed> feeds = feedMapper.selectList(Wrappers.<Feed>lambdaQuery()
                .eq(farmId != null, Feed::getFarmId, farmId)
                .orderByAsc(Feed::getId));
        List<FeedBrief> result = new ArrayList<>();
        for (Feed feed : feeds) {
            result.add(brief(feed));
        }
        return result;
    }

    @Override
    public DeductResult deductForFeeding(DeductCommand command) {
        if (command == null || command.feedId() == null || command.quantity() == null
                || command.quantity().signum() <= 0) {
            // 参数由模块06 先行校验；这里只保证端口不被误用
            return DeductResult.ok(null);
        }
        Feed feed = feedMapper.selectById(command.feedId());
        if (feed == null) {
            return new DeductResult(false, null, null);
        }
        BigDecimal stock = stockQty(feed);
        if (stock.compareTo(command.quantity()) < 0) {
            return DeductResult.insufficient(stock);
        }
        try {
            StockConsumeResult consumed = stockPort.deduct(new FeedStockCommand(
                    command.farmId() == null ? feed.getFarmId() : command.farmId(),
                    command.feedId(),
                    command.quantity(),
                    FeedStockCommand.BIZ_FEED_RECORD,
                    command.feedRecordId(),
                    command.batchId(),
                    command.penId(),
                    command.operatorId(),
                    null,
                    command.bizDate(),
                    null));
            return DeductResult.ok(consumed == null ? null : consumed.flowId());
        } catch (BizException e) {
            if (e.getCode() == ErrorCode.STOCK_NOT_ENOUGH.getCode()) {
                Feed fresh = feedMapper.selectById(command.feedId());
                return DeductResult.insufficient(fresh == null ? stock : stockQty(fresh));
            }
            throw e;
        }
    }

    @Override
    public void rollbackForFeeding(Long feedRecordId) {
        if (feedRecordId == null) {
            return;
        }
        // 模块09 按来源记录作废流水并冲回结存；没有有效流水时静默返回（幂等）
        stockPort.restore(FeedStockCommand.BIZ_FEED_RECORD, feedRecordId);
    }

    private static FeedBrief brief(Feed feed) {
        BigDecimal stock = stockQty(feed);
        boolean selectable = stock.signum() > 0;
        return new FeedBrief(feed.getId(),
                feed.getName(),
                feed.getUnit() == null || feed.getUnit().isBlank() ? UNIT_KG : feed.getUnit(),
                stock,
                feed.getRemainingDays(),
                Boolean.TRUE.equals(feed.getIsLowStock()),
                selectable,
                selectable ? null : EMPTY_REASON);
    }

    private static BigDecimal stockQty(Feed feed) {
        return feed.getStockQty() == null ? BigDecimal.ZERO : feed.getStockQty();
    }
}
