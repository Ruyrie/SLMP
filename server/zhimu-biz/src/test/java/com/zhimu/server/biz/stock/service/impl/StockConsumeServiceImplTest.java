package com.zhimu.server.biz.stock.service.impl;

import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.biz.stock.StockFixtures;
import com.zhimu.server.biz.stock.api.DrugLotOption;
import com.zhimu.server.biz.stock.api.DrugStockCommand;
import com.zhimu.server.biz.stock.api.FeedStockCommand;
import com.zhimu.server.biz.stock.api.StockConsumeResult;
import com.zhimu.server.biz.stock.api.StockCostAllocation;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.Feed;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.service.StockDrugLotService;
import com.zhimu.server.biz.stock.support.BatchAllocator;
import com.zhimu.server.biz.stock.support.StockErrors;
import com.zhimu.server.biz.stock.support.StockFlowWriter;
import com.zhimu.server.biz.stock.support.StockLedger;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.vo.QtyVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模块06 / 08 调用的库存端口（{@code FeedStockPort} / {@code DrugStockPort}）：
 * 先到期先出、结存不足 42201、过期 / 停用 42204、禁用药 42203、作废回滚幂等。
 * 对应 mock：lotopt_5301_use.json（优先出库）、err_42201_loss.json、err_42204_outbound.json、catalog_banned.json。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockConsumeServiceImplTest {

    private static final StockCostAllocation ALLOCATION =
            new StockCostAllocation(3001L, "ZM2610-031", 200, BigDecimal.ONE, new BigDecimal("505.60"));

    @Mock private StockLookups lookups;
    @Mock private StockLedger ledger;
    @Mock private StockFlowWriter flowWriter;
    @Mock private BatchAllocator allocator;
    @Mock private StockDrugLotService drugLotService;
    @Mock private DrugCatalogQueryService drugCatalogQueryService;

    private StockConsumeServiceImpl port;

    @BeforeEach
    void setUp() {
        StockFixtures.useClock();
        StockFixtures.loginOwner();
        port = new StockConsumeServiceImpl(lookups, ledger, flowWriter, allocator, drugLotService,
                drugCatalogQueryService);
        when(flowWriter.record(any(StockFlow.class))).thenAnswer(invocation -> {
            StockFlow flow = invocation.getArgument(0);
            flow.setId(9301L);
            return flow;
        });
        when(allocator.allocate(any(), any(), any(), any())).thenReturn(List.of(ALLOCATION));
        when(drugCatalogQueryService.isBanned(any(), any())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        StockFixtures.resetClock();
    }

    @Test
    void feedDeductWritesFeedUseFlowAndCost() {
        Feed feed = StockFixtures.feed(5101L, "育肥全价料", "658.000", "2105.60", "3.2000", 11);
        when(lookups.requireFeed(5101L)).thenReturn(feed);
        when(ledger.outFeed(feed, new BigDecimal("158"))).thenReturn(
                new StockLedger.Move(new BigDecimal("500"), new BigDecimal("1600.00"),
                        new BigDecimal("3.2000"), new BigDecimal("3.2000"), new BigDecimal("505.60"), 12));

        StockConsumeResult result = port.deduct(new FeedStockCommand(1001L, 5101L, new BigDecimal("158"),
                "FEED_RECORD", 7106L, 3001L, 2003L, 10003L, LocalDate.of(2026, 10, 19).atTime(7, 12),
                LocalDate.of(2026, 10, 19), "ZM2610-031 · 3 号育肥舍 早餐"));

        ArgumentCaptor<StockFlow> flow = ArgumentCaptor.forClass(StockFlow.class);
        verify(flowWriter).record(flow.capture());
        assertThat(flow.getValue().getMaterialType()).isEqualTo("FEED");
        assertThat(flow.getValue().getFlowType()).isEqualTo("FEED_USE");
        assertThat(flow.getValue().getDirection()).isEqualTo("OUT");
        assertThat(flow.getValue().getBizType()).isEqualTo("FEED_RECORD");
        assertThat(flow.getValue().getBizId()).isEqualTo(7106L);
        assertThat(flow.getValue().getIsFeedUsage()).isTrue();
        assertThat(flow.getValue().getIsBatchCost()).isTrue();
        assertThat(flow.getValue().getQty()).isEqualByComparingTo("158");
        assertThat(flow.getValue().getAmount()).isEqualByComparingTo("505.60");
        assertThat(result.flowId()).isEqualTo(9301L);
        assertThat(result.stockQtyAfter()).isEqualByComparingTo("500");
        assertThat(result.allocations()).hasSize(1);
        verify(flowWriter).writeCosts(any(), eq("FEED"), eq(2003L), any(), eq(LocalDate.of(2026, 10, 19)));
    }

    @Test
    void drugDeductUsesGivenLotAndFlowType() {
        Drug drug = StockFixtures.drug(5301L, "聚维酮碘溶液", "DISINFECTANT", 7301L, "L", "桶", "5.000");
        DrugLot lot = StockFixtures.lot(6301L, 5301L, "2025033", "2026-11-08", "40.000", "360.00",
                "9.0000", 5);
        when(lookups.requireDrug(5301L)).thenReturn(drug);
        when(lookups.requireLot(6301L)).thenReturn(lot);
        when(ledger.outLot(eq(lot), any(), eq("升"))).thenReturn(
                new StockLedger.Move(new BigDecimal("30"), new BigDecimal("270.00"),
                        new BigDecimal("9.0000"), new BigDecimal("9.0000"), new BigDecimal("90.00"), 6));

        StockConsumeResult result = port.deduct(new DrugStockCommand(1001L, 5301L, 6301L,
                new BigDecimal("10"), DrugStockCommand.FLOW_DISINFECT_USE,
                DrugStockCommand.BIZ_DISINFECT_RECORD, 8701L, null, 2002L, 10003L, null, null, null));

        ArgumentCaptor<StockFlow> flow = ArgumentCaptor.forClass(StockFlow.class);
        verify(flowWriter).record(flow.capture());
        assertThat(flow.getValue().getFlowType()).isEqualTo("DISINFECT_USE");
        assertThat(flow.getValue().getBizType()).isEqualTo("DISINFECT_RECORD");
        assertThat(flow.getValue().getMaterialType()).isEqualTo("DRUG");
        assertThat(flow.getValue().getIsFeedUsage()).isFalse();
        assertThat(result.lotNo()).isEqualTo("2025033");
        assertThat(result.unitPrice()).isEqualByComparingTo("9.0000");
    }

    @Test
    void drugDeductWithoutLotPicksFirstUsableLotByExpireDate() {
        Drug drug = StockFixtures.drug(5001L, "猪瘟活疫苗", "VACCINE", 7001L, "DOSE", "瓶", "10.000");
        when(lookups.requireDrug(5001L)).thenReturn(drug);
        // optionRecords 已按有效期升序返回（先到期先出）：6001 到期 2026-12-30 在 6002 之前
        when(drugLotService.optionRecords(5001L, "USE", null)).thenReturn(List.of(
                option(6001L, "2025081", "400.000", false),
                option(6002L, "2025113", "600.000", false)));
        DrugLot first = StockFixtures.lot(6001L, 5001L, "2025081", "2026-12-30", "400.000", "940.00",
                "2.3500", 5);
        when(lookups.requireLot(6001L)).thenReturn(first);
        when(ledger.outLot(eq(first), any(), anyString())).thenReturn(
                new StockLedger.Move(new BigDecimal("300"), new BigDecimal("705.00"),
                        new BigDecimal("2.3500"), new BigDecimal("2.3500"), new BigDecimal("235.00"), 6));

        StockConsumeResult result = port.deduct(new DrugStockCommand(1001L, 5001L, null,
                new BigDecimal("100"), DrugStockCommand.FLOW_VACCINE_USE,
                DrugStockCommand.BIZ_IMMUNE_RECORD, 8812L, 3003L, 2001L, 10004L, null, null, null));

        assertThat(result.lotNo()).isEqualTo("2025081");
        assertThat(result.stockQtyAfter()).isEqualByComparingTo("300");
        verify(lookups).requireLot(6001L);
    }

    @Test
    void noUsableLotIs42201() {
        Drug drug = StockFixtures.drug(5001L, "猪瘟活疫苗", "VACCINE", 7001L, "DOSE", "瓶", "10.000");
        when(lookups.requireDrug(5001L)).thenReturn(drug);
        when(drugLotService.optionRecords(5001L, "USE", null)).thenReturn(List.of(
                option(6001L, "2025081", "0.000", false)));

        assertThatThrownBy(() -> port.deduct(new DrugStockCommand(1001L, 5001L, null,
                new BigDecimal("10"), DrugStockCommand.FLOW_VACCINE_USE,
                DrugStockCommand.BIZ_IMMUNE_RECORD, 8812L, 3003L, 2001L, null, null, null, null)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(42201);
                    assertThat(e.getMessage()).contains("没有可用批号");
                });
        verify(ledger, never()).outLot(any(), any(), anyString());
    }

    @Test
    void bannedDrugIs42203BeforeAnyStockChange() {
        Drug drug = StockFixtures.drug(5401L, "盐酸克仑特罗", "VET_DRUG", 7206L, "G", "袋", "100.000");
        when(lookups.requireDrug(5401L)).thenReturn(drug);
        when(drugCatalogQueryService.isBanned(7206L, null)).thenReturn(true);

        assertThatThrownBy(() -> port.deduct(new DrugStockCommand(1001L, 5401L, 6301L,
                new BigDecimal("10"), DrugStockCommand.FLOW_MEDICATION_USE,
                DrugStockCommand.BIZ_MEDICATION_RECORD, 9001L, 3001L, 2003L, null, null, null, null)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(42203);
                    assertThat(e.getMessage()).isEqualTo("该药品属于禁用兽药，不能使用");
                });
        verify(ledger, never()).outLot(any(), any(), anyString());
    }

    @Test
    void recalledLotIs42204() {
        Drug drug = StockFixtures.drug(5003L, "口蹄疫灭活疫苗", "VACCINE", 7003L, "DOSE", "瓶", "10.000");
        DrugLot lot = StockFixtures.lot(5201L, 5003L, "2025047", "2027-04-20", "300.000", "840.00",
                "2.8000", 5);
        lot.setRecalledAt(LocalDate.of(2026, 10, 18).atTime(16, 20));
        when(lookups.requireDrug(5003L)).thenReturn(drug);
        when(lookups.requireLot(5201L)).thenReturn(lot);

        assertThatThrownBy(() -> port.deduct(new DrugStockCommand(1001L, 5003L, 5201L,
                new BigDecimal("10"), DrugStockCommand.FLOW_VACCINE_USE,
                DrugStockCommand.BIZ_IMMUNE_RECORD, 8813L, 3003L, 2001L, null, null, null, null)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(42204);
                    assertThat(e.getMessage()).isEqualTo("批号 2025047 已被平台停用，不能出库，只能登记报废");
                });
    }

    @Test
    void expiredLotIs42204() {
        Drug drug = StockFixtures.drug(5201L, "阿莫西林可溶性粉", "VET_DRUG", 7201L, "G", "袋", "500.000");
        DrugLot lot = StockFixtures.lot(6103L, 5201L, "2024118", "2026-10-12", "1500.000", "45.00",
                "0.0300", 5);
        when(lookups.requireDrug(5201L)).thenReturn(drug);
        when(lookups.requireLot(6103L)).thenReturn(lot);

        assertThatThrownBy(() -> port.deduct(new DrugStockCommand(1001L, 5201L, 6103L,
                new BigDecimal("10"), DrugStockCommand.FLOW_MEDICATION_USE,
                DrugStockCommand.BIZ_MEDICATION_RECORD, 9001L, 3001L, 2003L, null, null, null, null)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(42204);
                    assertThat(e.getMessage()).isEqualTo("批号 2024118 已过期，不能出库，只能登记报废");
                });
    }

    @Test
    void restoreRollsBackFeedAndDrugFlowsThenVoidsThem() {
        StockFlow feedFlow = StockFixtures.feedFlow(9301L, 5101L, "FEED_USE", "OUT", "158", "3.2000",
                "505.60", "FEED_RECORD", 7106L);
        StockFlow drugFlow = StockFixtures.feedFlow(9511L, 0L, "MANUAL_USE", "OUT", "10", "9.0000",
                "90.00", "OUTBOUND", 6511L);
        drugFlow.setMaterialType("DRUG");
        drugFlow.setFeedId(null);
        drugFlow.setDrugId(5301L);
        drugFlow.setDrugLotId(6301L);
        when(flowWriter.validFlows("FEED_RECORD", 7106L)).thenReturn(List.of(feedFlow));
        when(flowWriter.validFlows("OUTBOUND", 6511L)).thenReturn(List.of(drugFlow));
        Feed feed = StockFixtures.feed(5101L, "育肥全价料", "500.000", "1600.00", "3.2000", 12);
        DrugLot lot = StockFixtures.lot(6301L, 5301L, "2025033", "2026-11-08", "30.000", "270.00",
                "9.0000", 5);
        when(lookups.requireFeed(5101L)).thenReturn(feed);
        when(lookups.requireLot(6301L)).thenReturn(lot);
        Drug drug = StockFixtures.drug(5301L, "聚维酮碘溶液", "DISINFECTANT", 7301L, "L", "桶", "5.000");
        when(lookups.requireDrug(5301L)).thenReturn(drug);

        port.restore("FEED_RECORD", 7106L);
        port.restore("OUTBOUND", 6511L);

        verify(ledger).adjustFeed(eq(feed), any(), any());
        verify(ledger).adjustLot(eq(lot), any(), any(), eq("升"));
        verify(flowWriter).voidByBiz("FEED_RECORD", 7106L, "SOURCE_VOID");
        verify(flowWriter).voidByBiz("OUTBOUND", 6511L, "SOURCE_VOID");
    }

    @Test
    void restoreWithoutValidFlowIsQuietAndIdempotent() {
        when(flowWriter.validFlows(anyString(), any())).thenReturn(List.of());

        port.restore("FEED_RECORD", 7106L);
        port.restore("FEED_RECORD", 7106L);

        verify(ledger, never()).adjustFeed(any(), any(), any());
        verify(flowWriter, never()).voidByBiz(anyString(), any(), anyString());
    }

    private static DrugLotOption option(long lotId, String lotNo, String qty, boolean canGoScrap) {
        return new DrugLotOption(lotId, lotNo, LocalDate.of(2026, 12, 30), 72, "NORMAL", "正常",
                QtyVO.builder().value(new BigDecimal(qty)).unit("DOSE").unitText("头份").build(),
                false, false, canGoScrap, new BigDecimal("2.3500"), 5);
    }

    /** 显式引用错误工具，保证 42204 文案来源与接口实现一致 */
    @SuppressWarnings("unused")
    private static BizException lotUnavailable() {
        return StockErrors.lotUnavailable("2025047", true);
    }
}
