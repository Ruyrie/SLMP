package com.zhimu.server.biz.finance.support;

import com.zhimu.server.biz.finance.api.BatchSalePort;
import com.zhimu.server.biz.finance.api.StockCostPort;
import com.zhimu.server.biz.herd.api.BatchStockCommandService;
import com.zhimu.server.biz.stock.api.BatchCost;
import com.zhimu.server.biz.stock.api.StockCostQueryService;
import com.zhimu.server.common.enums.BizType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 模块10 两个跨模块端口的适配器：成本读取与出栏扣存栏。 */
class FinancePortAdaptersTest {

    @Test
    void stockCostAdapterMapsFeedAndDrugCost() {
        StockCostQueryService query = mock(StockCostQueryService.class);
        when(query.batchCost(null, 3001L)).thenReturn(new BatchCost(
                new BigDecimal("120000.00"), new BigDecimal("8000.00"),
                BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("128100.00")));

        StockCostAdapter adapter = new StockCostAdapter(query);
        StockCostPort.BatchCost cost = adapter.costOf(3001L);

        assertThat(cost.feed()).isEqualByComparingTo("120000.00");
        assertThat(cost.drug()).isEqualByComparingTo("8000.00");
        assertThat(cost.death()).isEqualByComparingTo("0");
        assertThat(cost.feedKg()).isEqualByComparingTo("0");
    }

    @Test
    void batchSaleAdapterBuildsSaleCommandAndReturnsRemaining() {
        BatchStockCommandService command = mock(BatchStockCommandService.class);
        when(command.sale(any())).thenReturn(new BatchStockCommandService.BatchSaleResult(
                3001L, "ACTIVE", 50, 150, 50, false, List.of(), List.of()));

        BatchSaleAdapter adapter = new BatchSaleAdapter(command);
        int remaining = adapter.saleOut(1001L, 3001L, LocalDate.of(2026, 10, 19),
                Map.of(2003L, 50), 9106L);

        assertThat(remaining).isEqualTo(150);
        verify(command).sale(any());
    }

    @Test
    void batchSaleAdapterRestoresByIdempotentKey() {
        BatchStockCommandService command = mock(BatchStockCommandService.class);
        BatchSaleAdapter adapter = new BatchSaleAdapter(command);

        adapter.restoreSale(1001L, 3001L, LocalDate.of(2026, 10, 19), Map.of(2003L, 50), 9106L);

        verify(command).restoreSale(BizType.SALE_ORDER, 9106L);
    }

    @Test
    void batchSaleAdapterReturnsMinusOneWhenPortCannotAdjust() {
        BatchSaleAdapter adapter = new BatchSaleAdapter(mock(BatchStockCommandService.class));
        assertThat(adapter.adjustSale(1001L, 3001L, LocalDate.of(2026, 10, 19),
                Map.of(2003L, -10), 9106L)).isEqualTo(-1);
        assertThat(adapter.penStocks(3001L)).isEmpty();
    }
}
