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
 * 创建出栏单调用 sale、作废调用 restoreSale；数量调整先恢复原扣减，
 * 再按审核后的完整栏舍分配重新出栏，由调用方事务保证原子性。
 */
@Component
@Primary
@RequiredArgsConstructor
public class BatchSaleAdapter implements BatchSalePort {

    private final BatchStockCommandService batchStockCommandService;
    private final com.zhimu.server.biz.finance.mapper.SaleOrderMapper orderMapper;

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
