package com.zhimu.server.biz.stock.service.impl;

import com.zhimu.server.biz.stock.StockFixtures;
import com.zhimu.server.biz.stock.api.BatchCost;
import com.zhimu.server.biz.stock.mapper.BatchMaterialCostMapper;
import com.zhimu.server.biz.stock.query.CostTypeSum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 模块10 批次成本只读汇总：按 cost_type 分组、NORMAL 明细、无明细时全 0.00。
 * 数据与 V9_10 示例一致（批次 3001：饲料 448.00 + 505.60，药品无；批次 3003：药品 280.00）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockCostQueryServiceImplTest {

    @Mock private BatchMaterialCostMapper costMapper;

    private StockCostQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        StockFixtures.useClock();
        StockFixtures.loginOwner();
        service = new StockCostQueryServiceImpl(costMapper);
    }

    @AfterEach
    void tearDown() {
        StockFixtures.resetClock();
        StockFixtures.logout();
    }

    @Test
    void sumsFeedDrugIntroAndOtherByCostType() {
        when(costMapper.sumByCostType(1001L, 3001L)).thenReturn(List.of(
                sum("FEED", "953.60"),
                sum("DRUG", "90.00"),
                sum("INTRO", "1200.00"),
                sum("OTHER", "12.34")));

        BatchCost cost = service.batchCost(1001L, 3001L);

        assertThat(cost.feedCost()).isEqualByComparingTo("953.60");
        assertThat(cost.drugCost()).isEqualByComparingTo("90.00");
        assertThat(cost.introCost()).isEqualByComparingTo("1200.00");
        assertThat(cost.otherCost()).isEqualByComparingTo("12.34");
        assertThat(cost.total()).isEqualByComparingTo("2255.94");
    }

    @Test
    void unknownCostTypeFallsIntoOtherAndTotalIsSumOfAllRows() {
        when(costMapper.sumByCostType(1001L, 3003L)).thenReturn(List.of(
                sum("FEED", "630.00"),
                sum("LOGISTICS", "35.00")));

        BatchCost cost = service.batchCost(1001L, 3003L);

        assertThat(cost.feedCost()).isEqualByComparingTo("630.00");
        assertThat(cost.drugCost()).isEqualByComparingTo("0");
        assertThat(cost.introCost()).isEqualByComparingTo("0");
        assertThat(cost.otherCost()).isEqualByComparingTo("35.00");
        assertThat(cost.total()).isEqualByComparingTo("665.00");
    }

    @Test
    void noDetailReturnsZeroWithTwoDecimals() {
        when(costMapper.sumByCostType(1001L, 3999L)).thenReturn(List.of());

        BatchCost cost = service.batchCost(1001L, 3999L);

        assertThat(cost.feedCost().toPlainString()).isEqualTo("0.00");
        assertThat(cost.drugCost().toPlainString()).isEqualTo("0.00");
        assertThat(cost.introCost().toPlainString()).isEqualTo("0.00");
        assertThat(cost.otherCost().toPlainString()).isEqualTo("0.00");
        assertThat(cost.total().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void nullBatchIdReturnsZeroWithoutQuery() {
        BatchCost cost = service.batchCost(1001L, null);

        assertThat(cost.total()).isEqualByComparingTo("0");
        verifyNoInteractions(costMapper);
    }

    @Test
    void farmIdFallsBackToCurrentFarmWhenMissing() {
        when(costMapper.sumByCostType(1001L, 3001L)).thenReturn(List.of(sum("FEED", "100.00")));

        BatchCost cost = service.batchCost(null, 3001L);

        assertThat(cost.feedCost()).isEqualByComparingTo("100.00");
        verify(costMapper).sumByCostType(1001L, 3001L);
    }

    private static CostTypeSum sum(String costType, String amount) {
        CostTypeSum row = new CostTypeSum();
        row.setCostType(costType);
        row.setAmount(new BigDecimal(amount));
        return row;
    }
}
