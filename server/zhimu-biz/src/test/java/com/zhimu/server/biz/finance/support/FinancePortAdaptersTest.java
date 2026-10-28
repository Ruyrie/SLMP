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

        when(query.feedKg(null, 3001L)).thenReturn(new BigDecimal("25930"));
        StockCostAdapter adapter = new StockCostAdapter(query);
        StockCostPort.BatchCost cost = adapter.costOf(3001L);

        assertThat(cost.feed()).isEqualByComparingTo("120000.00");
        assertThat(cost.drug()).isEqualByComparingTo("8000.00");
        assertThat(cost.death()).isEqualByComparingTo("0");
        assertThat(cost.feedKg()).isEqualByComparingTo("25930");
    }

    @Test
    void batchSaleAdapterBuildsSaleCommandAndReturnsRemaining() {
        BatchStockCommandService command = mock(BatchStockCommandService.class);
        when(command.sale(any())).thenReturn(new BatchStockCommandService.BatchSaleResult(
                3001L, "ACTIVE", 50, 150, 50, false, List.of(), List.of()));

        BatchSaleAdapter adapter = new BatchSaleAdapter(command, mock(com.zhimu.server.biz.herd.api.BatchQueryService.class),
                mock(com.zhimu.server.biz.finance.mapper.SaleOrderMapper.class));
        int remaining = adapter.saleOut(1001L, 3001L, LocalDate.of(2026, 10, 19),
                Map.of(2003L, 50), 9106L);

        assertThat(remaining).isEqualTo(150);
        verify(command).sale(any());
    }

    @Test
    void batchSaleAdapterRestoresByIdempotentKey() {
        BatchStockCommandService command = mock(BatchStockCommandService.class);
        BatchSaleAdapter adapter = new BatchSaleAdapter(command, mock(com.zhimu.server.biz.herd.api.BatchQueryService.class),
                mock(com.zhimu.server.biz.finance.mapper.SaleOrderMapper.class));

        adapter.restoreSale(1001L, 3001L, LocalDate.of(2026, 10, 19), Map.of(2003L, 50), 9106L);

        verify(command).restoreSale(BizType.SALE_ORDER, 9106L);
    }

    @Test
    void batchSaleAdapterReplaysCorrectedQuantityAfterRestoringOriginal() {
        var command = mock(BatchStockCommandService.class);
        var mapper = mock(com.zhimu.server.biz.finance.mapper.SaleOrderMapper.class);
        var order = new com.zhimu.server.biz.finance.entity.SaleOrder();
        order.setFarmId(1001L); order.setBatchId(3001L); order.setQuantity(40);
        order.setPenAllocations("[{\"pen_id\":2003,\"quantity\":40}]");
        when(mapper.selectById(9106L)).thenReturn(order);
        when(command.sale(any())).thenReturn(new BatchStockCommandService.BatchSaleResult(
                3001L, "ACTIVE", 40, 160, 40, false, List.of(), List.of()));
        BatchSaleAdapter adapter = new BatchSaleAdapter(command, mock(com.zhimu.server.biz.herd.api.BatchQueryService.class), mapper);
        assertThat(adapter.adjustSale(1001L, 3001L, LocalDate.of(2026, 10, 19),
                Map.of(2003L, -10), 9106L)).isEqualTo(160);
        var sequence = org.mockito.Mockito.inOrder(command);
        sequence.verify(command).restoreSale(BizType.SALE_ORDER, 9106L);
        sequence.verify(command).sale(org.mockito.ArgumentMatchers.argThat(value -> value.quantity() == 40));
    }
}
