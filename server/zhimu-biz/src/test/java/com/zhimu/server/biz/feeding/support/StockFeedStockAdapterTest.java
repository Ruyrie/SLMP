package com.zhimu.server.biz.feeding.support;

import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.stock.api.FeedStockCommand;
import com.zhimu.server.biz.stock.api.StockConsumeResult;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 模块06 → 模块09 的饲料库存适配：读 t_feed、按投喂记录扣减与回滚。 */
@ExtendWith(MockitoExtension.class)
class StockFeedStockAdapterTest {

    @Mock
    private com.zhimu.server.biz.stock.api.FeedStockPort stockPort;
    @Mock
    private FeedMapper feedMapper;

    private StockFeedStockAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new StockFeedStockAdapter(stockPort, feedMapper);
    }

    @Test
    void listFeedsMapsStockDaysAndDisablesEmptyFeed() {
        when(feedMapper.selectList(any())).thenReturn(List.of(
                feed(5101L, "育肥全价料", "500.000", 1, true),
                feed(5102L, "保育料", "1850.000", 12, false),
                feed(5103L, "哺乳仔猪教槽料", "0.000", null, false)));

        List<FeedStockPort.FeedBrief> briefs = adapter.listFeeds(1001L);

        assertThat(briefs).hasSize(3);
        assertThat(briefs.get(0).feedName()).isEqualTo("育肥全价料");
        assertThat(briefs.get(0).unit()).isEqualTo("KG");
        assertThat(briefs.get(0).stockQty()).isEqualByComparingTo("500.000");
        assertThat(briefs.get(0).remainingDays()).isEqualTo(1);
        assertThat(briefs.get(0).lowStock()).isTrue();
        assertThat(briefs.get(0).selectable()).isTrue();
        assertThat(briefs.get(0).disabledReason()).isNull();
        assertThat(briefs.get(2).selectable()).isFalse();
        assertThat(briefs.get(2).remainingDays()).isNull();
        assertThat(briefs.get(2).disabledReason()).isEqualTo("结存 0 千克，不能出库");
    }

    @Test
    void getFeedReturnsNullForUnknownOrForeignFeed() {
        when(feedMapper.selectById(9999L)).thenReturn(null);
        assertThat(adapter.getFeed(1001L, 9999L)).isNull();

        Feed other = feed(5101L, "育肥全价料", "500.000", 1, true);
        other.setFarmId(1002L);
        when(feedMapper.selectById(5101L)).thenReturn(other);
        assertThat(adapter.getFeed(1001L, 5101L)).isNull();
        assertThat(adapter.getFeed(1002L, 5101L)).isNotNull();
    }

    @Test
    void deductDelegatesToStockModuleWithFeedRecordBiz() {
        when(feedMapper.selectById(5101L)).thenReturn(feed(5101L, "育肥全价料", "500.000", 1, true));
        when(stockPort.deduct(any())).thenReturn(new StockConsumeResult(9301L, new BigDecimal("3.2000"),
                new BigDecimal("505.60"), new BigDecimal("341.000"), null, List.of()));

        FeedStockPort.DeductResult result = adapter.deductForFeeding(new FeedStockPort.DeductCommand(
                1001L, 5101L, new BigDecimal("158.00"), 3001L, 2003L, 7106L, LocalDate.of(2026, 10, 19), 10003L));

        assertThat(result.success()).isTrue();
        assertThat(result.stockFlowId()).isEqualTo(9301L);
        ArgumentCaptor<FeedStockCommand> captor = ArgumentCaptor.forClass(FeedStockCommand.class);
        verify(stockPort).deduct(captor.capture());
        FeedStockCommand command = captor.getValue();
        assertThat(command.farmId()).isEqualTo(1001L);
        assertThat(command.feedId()).isEqualTo(5101L);
        assertThat(command.qty()).isEqualByComparingTo("158.00");
        assertThat(command.bizType()).isEqualTo(FeedStockCommand.BIZ_FEED_RECORD);
        assertThat(command.bizId()).isEqualTo(7106L);
        assertThat(command.batchId()).isEqualTo(3001L);
        assertThat(command.penId()).isEqualTo(2003L);
        assertThat(command.operatorId()).isEqualTo(10003L);
        assertThat(command.bizDate()).isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    void deductReturnsInsufficientBeforeTouchingStockFlow() {
        when(feedMapper.selectById(5101L)).thenReturn(feed(5101L, "育肥全价料", "500.000", 1, true));

        FeedStockPort.DeductResult result = adapter.deductForFeeding(new FeedStockPort.DeductCommand(
                1001L, 5101L, new BigDecimal("600.00"), 3001L, 2003L, 7106L, LocalDate.of(2026, 10, 19), 10003L));

        assertThat(result.insufficient()).isTrue();
        assertThat(result.stockQty()).isEqualByComparingTo("500.000");
        verify(stockPort, never()).deduct(any());
    }

    @Test
    void deductConvertsStockNotEnoughIntoInsufficient() {
        when(feedMapper.selectById(5101L)).thenReturn(feed(5101L, "育肥全价料", "500.000", 1, true));
        when(stockPort.deduct(any())).thenThrow(new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                "超过当前结存 120 千克，出库后库存不能为负数"));

        FeedStockPort.DeductResult result = adapter.deductForFeeding(new FeedStockPort.DeductCommand(
                1001L, 5101L, new BigDecimal("158.00"), 3001L, 2003L, 7106L, LocalDate.of(2026, 10, 19), 10003L));

        assertThat(result.insufficient()).isTrue();
        assertThat(result.stockQty()).isEqualByComparingTo("500.000");
    }

    @Test
    void otherStockErrorsPropagate() {
        when(feedMapper.selectById(5101L)).thenReturn(feed(5101L, "育肥全价料", "500.000", 1, true));
        when(stockPort.deduct(any())).thenThrow(new BizException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> adapter.deductForFeeding(new FeedStockPort.DeductCommand(
                1001L, 5101L, new BigDecimal("158.00"), 3001L, 2003L, 7106L, LocalDate.of(2026, 10, 19), 10003L)))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void rollbackRestoresByBusinessRecord() {
        adapter.rollbackForFeeding(7106L);

        verify(stockPort).restore(FeedStockCommand.BIZ_FEED_RECORD, 7106L);
    }

    @Test
    void rollbackWithoutRecordIdDoesNothing() {
        adapter.rollbackForFeeding(null);

        verify(stockPort, never()).restore(any(), any());
    }

    private static Feed feed(Long id, String name, String stockQty, Integer remainingDays, boolean lowStock) {
        Feed feed = new Feed();
        feed.setId(id);
        feed.setFarmId(1001L);
        feed.setName(name);
        feed.setUnit("KG");
        feed.setStockQty(new BigDecimal(stockQty));
        feed.setRemainingDays(remainingDays);
        feed.setIsLowStock(lowStock);
        return feed;
    }
}
