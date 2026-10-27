package com.zhimu.server.biz.stock.support;

import com.zhimu.server.biz.stock.StockFixtures;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存记账口径（模块09 2.7）：移动加权平均、出库不超过结存（42201）、stock_version 条件更新与重试（40902）。
 * 对应 mock：feed_overview.json 的均价 3.2、drug_overview.json 的 6000 克、err_42201_loss.json。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockLedgerTest {

    @Mock private FeedMapper feedMapper;
    @Mock private DrugLotMapper drugLotMapper;

    private StockLedger ledger;

    @BeforeEach
    void setUp() {
        StockFixtures.useClock();
        ledger = new StockLedger(feedMapper, drugLotMapper, org.mockito.Mockito.mock(StockWarningReconciler.class));
        when(feedMapper.updateStock(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(drugLotMapper.updateStock(any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        StockFixtures.resetClock();
    }

    @Test
    void feedInboundUsesMovingWeightedAverage() {
        // 500 千克 / 1,600 元（均价 3.2）再入库 2,000 千克 × 3.20 = 6,400 元
        Feed feed = StockFixtures.feed(5101L, "育肥全价料", "500.000", "1600.00", "3.2000", 12);

        StockLedger.Move move = ledger.inFeed(feed, new BigDecimal("2000"), new BigDecimal("6400.00"));

        assertThat(move.qtyAfter()).isEqualByComparingTo("2500");
        assertThat(move.amountAfter()).isEqualByComparingTo("8000.00");
        assertThat(move.avgPrice()).isEqualByComparingTo("3.2000");
        assertThat(move.unitPrice()).isEqualByComparingTo("3.2000");
        assertThat(move.version()).isEqualTo(13);
        // 两次入库单价不同时按金额 / 数量重算：500×3.2 + 100×4.0 = 2,000 → 均价 3.3333
        StockLedger.Move moved = ledger.inFeed(feed, new BigDecimal("100"), new BigDecimal("400.00"));
        assertThat(moved.qtyAfter()).isEqualByComparingTo("600");
        assertThat(moved.avgPrice()).isEqualByComparingTo("3.3333");
        verify(feedMapper, times(2)).updateStock(eq(5101L), any(), any(), any(), any(), any(LocalDateTime.class));
    }

    @Test
    void feedOutboundUsesCurrentAveragePrice() {
        Feed feed = StockFixtures.feed(5101L, "育肥全价料", "500.000", "1600.00", "3.2000", 12);

        StockLedger.Move move = ledger.outFeed(feed, new BigDecimal("158"));

        assertThat(move.qtyAfter()).isEqualByComparingTo("342");
        assertThat(move.unitPrice()).isEqualByComparingTo("3.2000");
        assertThat(move.amount()).isEqualByComparingTo("505.60");
        assertThat(move.amountAfter()).isEqualByComparingTo("1094.40");
        assertThat(move.version()).isEqualTo(13);
    }

    @Test
    void outboundExactlyToZeroTakesRemainingAmount() {
        Feed feed = StockFixtures.feed(5101L, "育肥全价料", "500.000", "1600.00", "3.2000", 12);

        StockLedger.Move move = ledger.outFeed(feed, new BigDecimal("500"));

        assertThat(move.qtyAfter()).isEqualByComparingTo("0");
        assertThat(move.amount()).isEqualByComparingTo("1600.00");
        assertThat(move.amountAfter()).isEqualByComparingTo("0");
        assertThat(move.unitPrice()).isEqualByComparingTo("3.2000");
    }

    @Test
    void feedOutboundBeyondStockIs42201() {
        Feed feed = StockFixtures.feed(5101L, "育肥全价料", "500.000", "1600.00", "3.2000", 12);

        assertThatThrownBy(() -> ledger.outFeed(feed, new BigDecimal("600")))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(42201);
                    assertThat(e.getMessage()).isEqualTo("超过当前结存 500 千克，出库后库存不能为负数");
                });
        verify(feedMapper, never()).updateStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void versionConflictRetriesThenSucceeds() {
        Feed feed = StockFixtures.feed(5101L, "育肥全价料", "500.000", "1600.00", "3.2000", 12);
        Feed fresh = StockFixtures.feed(5101L, "育肥全价料", "480.000", "1536.00", "3.2000", 13);
        when(feedMapper.selectById(5101L)).thenReturn(fresh);
        when(feedMapper.updateStock(any(), any(), any(), any(), any(), any())).thenReturn(0, 1);

        StockLedger.Move move = ledger.outFeed(feed, new BigDecimal("50"));

        // 第一次用版本 12 更新失败，重读版本 13 后按 480 千克重算
        assertThat(move.qtyAfter()).isEqualByComparingTo("430");
        assertThat(move.version()).isEqualTo(14);
        verify(feedMapper, times(2)).updateStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void versionConflictAfterThreeAttemptsIs40902() {
        Feed feed = StockFixtures.feed(5101L, "育肥全价料", "500.000", "1600.00", "3.2000", 12);
        when(feedMapper.selectById(5101L)).thenReturn(feed);
        when(feedMapper.updateStock(any(), any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> ledger.outFeed(feed, new BigDecimal("10")))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(40902));
        verify(feedMapper, times(3)).updateStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void drugLotInboundAndOutboundUseLotStockAndAveragePrice() {
        DrugLot lot = StockFixtures.lot(6001L, 5001L, "2025081", "2026-12-30", "400.000", "940.00",
                "2.3500", 5);

        StockLedger.Move in = ledger.inLot(lot, new BigDecimal("600"), new BigDecimal("1440.00"));
        assertThat(in.qtyAfter()).isEqualByComparingTo("1000");
        assertThat(in.avgPrice()).isEqualByComparingTo("2.3800");

        StockLedger.Move out = ledger.outLot(lot, new BigDecimal("200"), "头份");
        assertThat(out.qtyAfter()).isEqualByComparingTo("200");
        assertThat(out.amount()).isEqualByComparingTo("470.00");
        assertThat(out.amountAfter()).isEqualByComparingTo("470.00");
    }

    @Test
    void drugLotOutboundBeyondStockIs42201WithUnitText() {
        DrugLot lot = StockFixtures.lot(5201L, 5003L, "2025047", "2027-04-20", "300.000", "840.00",
                "2.8000", 5);

        assertThatThrownBy(() -> ledger.outLot(lot, new BigDecimal("400"), "头份"))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(42201);
                    assertThat(e.getMessage()).contains("超过当前结存 300 头份");
                });
    }

    @Test
    void adjustFeedRecalculatesAverageAndRejectsNegativeStock() {
        Feed feed = StockFixtures.feed(5101L, "育肥全价料", "500.000", "1600.00", "3.2000", 12);

        StockLedger.Move move = ledger.adjustFeed(feed, new BigDecimal("-12"), new BigDecimal("-38.40"));
        assertThat(move.qtyAfter()).isEqualByComparingTo("488");
        assertThat(move.amountAfter()).isEqualByComparingTo("1561.60");
        assertThat(move.avgPrice()).isEqualByComparingTo("3.2000");

        assertThatThrownBy(() -> ledger.adjustFeed(feed, new BigDecimal("-600"), BigDecimal.ZERO))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(42201));
    }

    @Test
    void qtyAndAmountScalesMatchContract() {
        assertThat(StockQty.qty(new BigDecimal("1.5"))).isEqualByComparingTo("1.5");
        assertThat(StockQty.qty(new BigDecimal("500.000")).toPlainString()).isEqualTo("500");
        assertThat(StockQty.amount(new BigDecimal("505.605"))).isEqualByComparingTo("505.61");
        assertThat(StockQty.price(new BigDecimal("3.2"))).isEqualByComparingTo("3.2000");
        assertThat(StockQty.plain(new BigDecimal("1000"))).isEqualTo("1,000");
        assertThat(StockQty.plain(new BigDecimal("4410"))).isEqualTo("4,410");
        // 药品 QtyVO 的包装换算：6000 克 ÷ 500 克/袋 = 12 袋、余 0
        var boxed = StockQty.drug(new BigDecimal("6000"), "G", "袋", new BigDecimal("500"));
        assertThat(boxed.getValue()).isEqualByComparingTo("6000");
        assertThat(boxed.getUnit()).isEqualTo("G");
        assertThat(boxed.getUnitText()).isEqualTo("克");
        assertThat(boxed.getPackCount()).isEqualTo(12);
        assertThat(boxed.getRestValue()).isEqualByComparingTo("0");
        assertThat(StockQty.drug(new BigDecimal("1250"), "G", "袋", new BigDecimal("500")).getRestValue())
                .isEqualByComparingTo("250");
    }
}
