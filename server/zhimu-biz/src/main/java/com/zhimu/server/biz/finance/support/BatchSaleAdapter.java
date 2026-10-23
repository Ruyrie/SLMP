package com.zhimu.server.biz.finance.support;

import com.zhimu.server.biz.finance.api.BatchSalePort;
import com.zhimu.server.biz.herd.api.BatchStockCommandService;
import com.zhimu.server.common.enums.BizType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模块10 出栏单写入模块04 存栏的适配器。
 * 创建出栏单调用 sale、作废调用 restoreSale；数量调整暂未映射到模块04 的幂等扣减命令，
 * 返回 -1 由调用方按批次存栏兜底，避免重复扣减。
 */
@Component
@Primary
@RequiredArgsConstructor
public class BatchSaleAdapter implements BatchSalePort {

    private final BatchStockCommandService batchStockCommandService;

    @Override
    public List<PenStock> penStocks(Long batchId) {
        // 模块04 未提供按批次查各栏舍存栏的只读端口，返回空列表由调用方用批次 currentCount 兜底
        return List.of();
    }

    @Override
    public int saleOut(Long farmId, Long batchId, LocalDate saleDate, Map<Long, Integer> penQuantities,
                       Long saleOrderId) {
        if (batchId == null || saleOrderId == null || penQuantities == null || penQuantities.isEmpty()) {
            return -1;
        }
        List<BatchStockCommandService.SaleStockItem> items = new ArrayList<>();
        int total = 0;
        for (Map.Entry<Long, Integer> entry : penQuantities.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            items.add(new BatchStockCommandService.SaleStockItem(entry.getKey(), entry.getValue()));
            total += entry.getValue();
        }
        if (items.isEmpty()) {
            return -1;
        }
        BatchStockCommandService.BatchSaleResult result = batchStockCommandService.sale(
                new BatchStockCommandService.SaleStockCommand(batchId, BizType.SALE_ORDER, saleOrderId, saleDate,
                        total, items, null));
        return result == null ? -1 : result.currentCount();
    }

    @Override
    public int restoreSale(Long farmId, Long batchId, LocalDate saleDate, Map<Long, Integer> penQuantities,
                           Long saleOrderId) {
        if (saleOrderId == null) {
            return -1;
        }
        batchStockCommandService.restoreSale(BizType.SALE_ORDER, saleOrderId);
        return -1;
    }

    @Override
    public int adjustSale(Long farmId, Long batchId, LocalDate saleDate, Map<Long, Integer> penQuantities,
                          Long saleOrderId) {
        // 模块04 的扣减命令按（业务类型、业务 ID、批次）幂等，不能直接用 delta 再扣一次
        return -1;
    }
}
