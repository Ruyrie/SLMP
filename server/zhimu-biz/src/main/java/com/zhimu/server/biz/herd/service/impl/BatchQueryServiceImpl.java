package com.zhimu.server.biz.herd.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 批次查询实现（供模块05–10）。跨养殖场按 ID 查询时用 TenantSkip 并显式按 farm_id 过滤，
 * 避免调用方没有当前养殖场上下文时被多租户插件误过滤。
 */
@Service
@RequiredArgsConstructor
public class BatchQueryServiceImpl implements BatchQueryService {

    private final BatchMapper batchMapper;
    private final BatchPenMapper batchPenMapper;

    @Override
    public BatchInfo requireBatch(Long batchId) {
        if (batchId == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Batch batch = TenantSkip.call(() -> batchMapper.selectById(batchId));
        if (batch == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toInfo(batch, penIdsOf(batch.getId()));
    }

    @Override
    public List<BatchInfo> listActive(Long farmId) {
        if (farmId == null) {
            return List.of();
        }
        List<Batch> batches = TenantSkip.call(() -> batchMapper.selectList(
                Wrappers.<Batch>lambdaQuery()
                        .eq(Batch::getFarmId, farmId)
                        .eq(Batch::getStatus, "ACTIVE")
                        .orderByAsc(Batch::getEntryDate)
                        .orderByAsc(Batch::getId)));
        if (batches.isEmpty()) {
            return List.of();
        }
        List<Long> batchIds = batches.stream().map(Batch::getId).toList();
        List<BatchPen> pens = TenantSkip.call(() -> batchPenMapper.selectList(
                Wrappers.<BatchPen>lambdaQuery()
                        .in(BatchPen::getBatchId, batchIds)
                        .gt(BatchPen::getStockCount, 0)
                        .orderByAsc(BatchPen::getPenId)));
        Map<Long, List<Long>> penIds = pens.stream().collect(Collectors.groupingBy(BatchPen::getBatchId,
                Collectors.mapping(BatchPen::getPenId, Collectors.toList())));
        List<BatchInfo> result = new ArrayList<>(batches.size());
        for (Batch batch : batches) {
            result.add(toInfo(batch, penIds.getOrDefault(batch.getId(), List.of())));
        }
        return result;
    }

    @Override
    public boolean isActive(Long batchId) {
        if (batchId == null) {
            return false;
        }
        Batch batch = TenantSkip.call(() -> batchMapper.selectById(batchId));
        return batch != null && "ACTIVE".equals(batch.getStatus());
    }

    private List<Long> penIdsOf(Long batchId) {
        List<BatchPen> pens = TenantSkip.call(() -> batchPenMapper.selectList(
                Wrappers.<BatchPen>lambdaQuery()
                        .eq(BatchPen::getBatchId, batchId)
                        .gt(BatchPen::getStockCount, 0)
                        .orderByAsc(BatchPen::getPenId)));
        return pens.stream().map(BatchPen::getPenId).toList();
    }

    private static BatchInfo toInfo(Batch batch, List<Long> penIds) {
        return new BatchInfo(batch.getFarmId(), batch.getId(), batch.getBatchNo(), batch.getBreedId(),
                batch.getCategoryId(), batch.getEntryDate(), batch.getCurrentCount(), batch.getStatus(), penIds);
    }
}
