package com.zhimu.server.biz.finance.support;

import com.zhimu.server.biz.finance.api.StockCostPort;
import com.zhimu.server.biz.stock.api.StockCostQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 模块10 核算读取模块09 批次物资成本的适配器。
 * 模块09 的 BatchCost 含 feed/drug/intro/other，模块10 的核算口径只用 feed 与 drug；
 * feedKg 当前不从成本表推导，返回 0；死淘成本暂未进入核算口径。
 */
@Component
@Primary
@RequiredArgsConstructor
public class StockCostAdapter implements StockCostPort {

    private final StockCostQueryService stockCostQueryService;

    @Override
    public BatchCost costOf(Long batchId) {
        if (batchId == null) {
            return BatchCost.ZERO;
        }
        var cost = stockCostQueryService.batchCost(null, batchId);
        if (cost == null) {
            return BatchCost.ZERO;
        }
        return new BatchCost(nz(cost.feedCost()), BigDecimal.ZERO, nz(cost.drugCost()), BigDecimal.ZERO);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
