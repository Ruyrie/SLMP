package com.zhimu.server.biz.stock.support;

import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.stock.api.StockCostAllocation;
import com.zhimu.server.biz.stock.mapper.StockPenBatchMapper;
import com.zhimu.server.biz.stock.query.PenBatchRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 批次成本分摊（模块09 2.7 节）：有批次时一行“全部计入”；有栏舍时按舍内各在栏批次的存栏比例分摊，
 * 比例 4 位小数、金额到分、尾差计入最后一个批次；空栏不写明细。
 */
@Component
@RequiredArgsConstructor
public class BatchAllocator {

    private final BatchQueryService batchQueryService;
    private final StockPenBatchMapper penBatchMapper;

    /**
     * @param farmId      养殖场 ID
     * @param batchId     关联批次（与 penId 二选一）
     * @param penId       关联栏舍（与 batchId 二选一）
     * @param totalAmount 分摊金额（元）
     * @return 分摊行；既无批次也无不含在栏批次的栏舍时返回空列表
     */
    public List<StockCostAllocation> allocate(Long farmId, Long batchId, Long penId, BigDecimal totalAmount) {
        BigDecimal amount = StockQty.amount(StockQty.zeroIfNull(totalAmount));
        if (batchId != null) {
            BatchInfo batch = batchQueryService.requireBatch(batchId);
            return List.of(new StockCostAllocation(batchId, batch.batchNo(), batch.currentCount(),
                    BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP), amount));
        }
        if (penId == null) {
            return List.of();
        }
        List<PenBatchRow> rows = penBatchMapper.listStockedBatches(farmId, penId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        int totalHeads = rows.stream().mapToInt(row -> row.getStockCount() == null ? 0 : row.getStockCount()).sum();
        if (totalHeads <= 0) {
            return List.of();
        }
        List<StockCostAllocation> result = new ArrayList<>(rows.size());
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < rows.size(); i++) {
            PenBatchRow row = rows.get(i);
            int heads = row.getStockCount() == null ? 0 : row.getStockCount();
            BigDecimal ratio = BigDecimal.valueOf(heads)
                    .divide(BigDecimal.valueOf(totalHeads), 4, RoundingMode.HALF_UP);
            BigDecimal rowAmount;
            if (i == rows.size() - 1) {
                rowAmount = amount.subtract(allocated);
            } else {
                rowAmount = StockQty.amount(amount.multiply(ratio));
                allocated = allocated.add(rowAmount);
            }
            BatchInfo batch = batchQueryService.requireBatch(row.getBatchId());
            result.add(new StockCostAllocation(row.getBatchId(), batch.batchNo(), heads, ratio, rowAmount));
        }
        return result;
    }
}
