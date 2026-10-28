package com.zhimu.server.biz.finance.support;

import com.zhimu.server.biz.finance.api.BatchSalePort;
import com.zhimu.server.biz.herd.api.BatchQueryService;
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
 * 各栏舍可出数读模块04 的 t_batch_pen；创建出栏单调用 sale、作废调用 restoreSale（返回恢复后的批次存栏）；
 * 数量调整先恢复原扣减，再按审核后的完整栏舍分配重新出栏，由调用方事务保证原子性。
 */
@Component
@Primary
@RequiredArgsConstructor
public class BatchSaleAdapter implements BatchSalePort {

    private final BatchStockCommandService batchStockCommandService;
    private final BatchQueryService batchQueryService;
    private final com.zhimu.server.biz.finance.mapper.SaleOrderMapper orderMapper;

    /** 读模块04 的 t_batch_pen：各栏舍真实在栏数（跨栏舍批次出栏按此分配与校验） */
    @Override
    public List<PenStock> penStocks(Long batchId) {
        if (batchId == null) {
            return List.of();
        }
        List<PenStock> result = new ArrayList<>();
        for (BatchQueryService.PenStock stock : batchQueryService.penStocks(batchId)) {
            result.add(new PenStock(stock.penId(), null, stock.stockCount()));
        }
        return result;
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
        return currentCount(batchId);
    }

    /** 恢复后的批次存栏；批次不存在时返回 -1（调用方不依赖返回值） */
    private int currentCount(Long batchId) {
        if (batchId == null) {
            return -1;
        }
        try {
            Integer count = batchQueryService.requireBatch(batchId).currentCount();
            return count == null ? 0 : count;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    @Override
    public int adjustSale(Long farmId, Long batchId, LocalDate saleDate, Map<Long, Integer> penQuantities,
                          Long saleOrderId) {
        var order = com.zhimu.server.framework.mybatis.TenantSkip.call(() -> orderMapper.selectById(saleOrderId));
        if (order == null || !java.util.Objects.equals(farmId, order.getFarmId())
                || !java.util.Objects.equals(batchId, order.getBatchId())) {
            throw new com.zhimu.server.common.error.BizException(com.zhimu.server.common.error.ErrorCode.NOT_FOUND);
        }
        var allocations = FinanceSupport.parse(order.getPenAllocations(),
                com.zhimu.server.biz.finance.vo.SaleOrderVos.PenAllocation[].class);
        Map<Long, Integer> corrected = new java.util.LinkedHashMap<>();
        if (allocations != null) for (var allocation : allocations) {
            if (allocation.getQuantity() != null && allocation.getQuantity() > 0)
                corrected.merge(allocation.getPenId(), allocation.getQuantity(), Integer::sum);
        }
        if (corrected.values().stream().mapToInt(Integer::intValue).sum() != order.getQuantity()) {
            throw new com.zhimu.server.common.error.BizException(com.zhimu.server.common.error.ErrorCode.STATE_CHANGED,
                    "栏舍出栏数量与总数不一致，请核对后重新审核");
        }
        batchStockCommandService.restoreSale(BizType.SALE_ORDER, saleOrderId);
        return saleOut(farmId, batchId, saleDate, corrected, saleOrderId);
    }
}
