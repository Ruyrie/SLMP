package com.zhimu.server.biz.stock.service;

import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.entity.StockLoss;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.FeedMapper;
import com.zhimu.server.biz.stock.mapper.RecallProgressMapper;
import com.zhimu.server.biz.stock.mapper.StockLossMapper;
import com.zhimu.server.biz.stock.request.DrugScrapRequest;
import com.zhimu.server.biz.stock.service.impl.StockLossServiceImpl;
import com.zhimu.server.biz.stock.support.StockFlowWriter;
import com.zhimu.server.biz.stock.support.StockLedger;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.biz.stock.support.StockWarningReconciler;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises the real scrap service and ledger together; only persistence and warning I/O are mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockLossServiceImplTest {
    @Mock StockLossMapper losses;
    @Mock FeedMapper feeds;
    @Mock DrugMapper drugs;
    @Mock DrugLotMapper lots;
    @Mock StockFlowWriter flows;
    @Mock StockLookups lookups;
    @Mock RecallProgressMapper recalls;
    @Mock StockWarningReconciler warnings;
    private StockLossServiceImpl service;
    private Drug drug;
    private DrugLot lot;
    private StockLoss saved;
    private StockFlow flow;

    @BeforeEach
    void setup() {
        service = new StockLossServiceImpl(losses, feeds, drugs,
                new StockLedger(feeds, lots, warnings), flows, lookups, recalls);
        drug = new Drug();
        drug.setId(61L); drug.setFarmId(1L); drug.setName("阿莫西林可溶性粉");
        drug.setUnit("G"); drug.setPackUnit("袋"); drug.setPackSize(new BigDecimal("500"));
        lot = new DrugLot();
        lot.setId(62L); lot.setFarmId(1L); lot.setDrugId(61L); lot.setLotNo("2024118");
        lot.setVersion(0); lot.setStockQty(new BigDecimal("3000"));
        lot.setStockAmount(new BigDecimal("90")); lot.setAvgPrice(new BigDecimal("0.03"));
        lot.setFirstInboundDate(BeijingTime.today().minusDays(365));
        lot.setExpireDate(BeijingTime.today().minusDays(1));
        when(lookups.farmId()).thenReturn(1L);
        when(lookups.userId()).thenReturn(10L);
        when(lookups.requireLot(62L)).thenAnswer(i -> lot);
        when(lookups.requireDrug(61L)).thenReturn(drug);
        when(drugs.selectById(61L)).thenReturn(drug);
        when(lots.updateStock(any(), any(), any(), any(), any(), any())).thenAnswer(i -> {
            DrugLot next = new DrugLot();
            BeanUtils.copyProperties(lot, next);
            next.setStockQty(i.getArgument(2)); next.setStockAmount(i.getArgument(3));
            next.setAvgPrice(i.getArgument(4)); next.setVersion((Integer)i.getArgument(1) + 1);
            lot = next;
            return 1;
        });
        when(losses.insert(any(StockLoss.class))).thenAnswer(i -> {
            saved = i.getArgument(0); saved.setId(63L); return 1;
        });
        when(lookups.requireLoss(anyLong())).thenAnswer(i -> saved);
        when(flows.record(any())).thenAnswer(i -> {
            flow = i.getArgument(0); flow.setId(64L); return flow;
        });
    }

    @Test
    void sixBagsScrapThreeThousandGramsAndClearQuantityAndAmount() {
        var result = service.createDrugScrap(request("6", "PACK"), UUID.randomUUID().toString());
        assertThat(saved.getInputUnit()).isEqualTo("PACK");
        assertThat(saved.getInputQty()).isEqualByComparingTo("6");
        assertThat(saved.getQty()).isEqualByComparingTo("3000");
        assertThat(saved.getAmount()).isEqualByComparingTo("90");
        assertThat(flow.getQty()).isEqualByComparingTo("3000");
        assertThat(flow.getStockQtyAfter()).isZero();
        assertThat(flow.getStockAmountAfter()).isZero();
        assertThat(result.getRemaining().getValue()).isZero();
        assertThat(lot.getStockAmount()).isZero();
        verify(warnings).reconcileDrug(1L, 61L);
    }

    @Test
    void partialPackScrapKeepsExactRemainingStock() {
        var result = service.createDrugScrap(request("2", "PACK"), UUID.randomUUID().toString());
        assertThat(saved.getQty()).isEqualByComparingTo("1000");
        assertThat(result.getRemaining().getValue()).isEqualByComparingTo("2000");
        assertThat(lot.getStockAmount()).isEqualByComparingTo("60");
    }

    @Test
    void exactRemainderUsesMeasureAndClearsResidualCents() {
        lot.setStockQty(new BigDecimal("2982.051"));
        lot.setStockAmount(new BigDecimal("89.46"));
        var result = service.createDrugScrap(request("2982.051", "MEASURE"), UUID.randomUUID().toString());
        assertThat(saved.getQty()).isEqualByComparingTo("2982.051");
        assertThat(saved.getAmount()).isEqualByComparingTo("89.46");
        assertThat(result.getRemaining().getValue()).isZero();
        assertThat(lot.getStockAmount()).isZero();
        assertThatThrownBy(() -> service.createDrugScrap(request("1", "MEASURE"), "repeat"))
                .isInstanceOf(BizException.class).hasMessageContaining("结存已为 0");
    }

    @Test
    void missingInputUnitRemainsCompatibleWithMeasureClients() {
        service.createDrugScrap(request("6", null), UUID.randomUUID().toString());
        assertThat(saved.getInputUnit()).isEqualTo("MEASURE");
        assertThat(saved.getQty()).isEqualByComparingTo("6");
        assertThat(lot.getStockQty()).isEqualByComparingTo("2994");
    }

    @Test
    void overStockIsRejectedAfterPackConversionWithoutCreatingLoss() {
        assertThatThrownBy(() -> service.createDrugScrap(request("7", "PACK"), "oversize"))
                .isInstanceOf(BizException.class).hasMessageContaining("超过当前结存 3,000 克");
        verify(losses, never()).insert(any(StockLoss.class));
        assertThat(lot.getStockQty()).isEqualByComparingTo("3000");
    }

    @Test
    void rejectsUnknownUnitMissingPackAndRoundingToZero() {
        assertThatThrownBy(() -> service.createDrugScrap(request("1", "BAG"), "invalid"))
                .isInstanceOf(BizException.class).hasMessageContaining("MEASURE 或 PACK");
        drug.setPackUnit(null);
        assertThatThrownBy(() -> service.createDrugScrap(request("1", "PACK"), "invalid"))
                .isInstanceOf(BizException.class).hasMessageContaining("未设置包装");
        drug.setPackUnit("袋"); drug.setPackSize(new BigDecimal("0.001"));
        assertThatThrownBy(() -> service.createDrugScrap(request("0.001", "PACK"), "invalid"))
                .isInstanceOf(BizException.class).hasMessageContaining("最多保留 3 位小数");
        verify(losses, never()).insert(any(StockLoss.class));
    }

    @Test
    void rejectsFractionalDosesAndStaleStockVersion() {
        drug.setUnit("DOSE");
        assertThatThrownBy(() -> service.createDrugScrap(request("1.5", "MEASURE"), "invalid"))
                .isInstanceOf(BizException.class).hasMessageContaining("头份必须是整数");
        DrugScrapRequest stale = request("6", "PACK"); stale.setStockVersion(9);
        assertThatThrownBy(() -> service.createDrugScrap(stale, "stale"))
                .isInstanceOf(BizException.class).hasMessageContaining("库存已变化");
        verify(losses, never()).insert(any(StockLoss.class));
    }

    private DrugScrapRequest request(String qty, String inputUnit) {
        DrugScrapRequest request = new DrugScrapRequest();
        request.setDrugLotId(62L); request.setInputQty(new BigDecimal(qty)); request.setInputUnit(inputUnit);
        request.setReason("EXPIRED"); request.setScrapDate(BeijingTime.today()); request.setStockVersion(lot.getVersion());
        return request;
    }
}
