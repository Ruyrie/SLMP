package com.zhimu.server.biz.finance.api;

import com.zhimu.server.biz.finance.support.FinanceConst;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 跨模块端口的 no-op 兜底：模块04（出栏扣存栏）与模块09（物资成本）后续实现后，
 * 用 @ConditionalOnMissingBean 让真实实现覆盖这里的 Bean。
 */
@Configuration
public class FinancePortConfig {

    @Bean
    @ConditionalOnMissingBean(BatchSalePort.class)
    public BatchSalePort noopBatchSalePort() {
        return new BatchSalePort() {
            @Override
            public List<PenStock> penStocks(Long batchId) {
                return List.of();
            }

            @Override
            public int saleOut(Long farmId, Long batchId, LocalDate saleDate, Map<Long, Integer> penQuantities,
                               Long saleOrderId) {
                return -1;
            }

            @Override
            public int restoreSale(Long farmId, Long batchId, LocalDate saleDate, Map<Long, Integer> penQuantities,
                                   Long saleOrderId) {
                return -1;
            }

            @Override
            public int adjustSale(Long farmId, Long batchId, LocalDate saleDate, Map<Long, Integer> penQuantities,
                                  Long saleOrderId) {
                return -1;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(StockCostPort.class)
    public StockCostPort noopStockCostPort() {
        return batchId -> new StockCostPort.BatchCost(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}
