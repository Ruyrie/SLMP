package com.zhimu.server.biz.herd.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchEvent;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.mapper.BatchEventMapper;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
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
    private final BatchEventMapper eventMapper;

    @Override
    public AccountingBasis accountingBasis(Long batchId) {
        Batch batch = TenantSkip.call(() -> batchMapper.selectById(batchId));
        if (batch == null) throw new BizException(ErrorCode.NOT_FOUND);
        return new AccountingBasis(batch.getIntroUnitPrice(), batch.getEntryAvgWeight(), batch.getEndedAt());
    }

    @Override
    public int deathCount(Collection<Long> farmIds, LocalDate start, LocalDate end) {
        if (farmIds == null || farmIds.isEmpty() || start == null || end == null) {
            return 0;
        }
        return TenantSkip.call(() -> eventMapper.selectList(Wrappers.<BatchEvent>lambdaQuery()
                        .in(BatchEvent::getFarmId, farmIds)
                        .eq(BatchEvent::getEventType, "DEATH")
                        .eq(BatchEvent::getStatus, "EFFECTIVE")
                        .between(BatchEvent::getEventDate, start, end)))
                .stream().mapToInt(e -> e.getQuantity() == null ? 0 : e.getQuantity()).sum();
    }

    @Override
    public int stockCountAt(Collection<Long> farmIds, LocalDate date) {
        if (farmIds == null || farmIds.isEmpty() || date == null) {
            return 0;
        }
        return TenantSkip.call(() -> {
            int stock = batchMapper.selectList(Wrappers.<Batch>lambdaQuery().in(Batch::getFarmId, farmIds))
                    .stream().mapToInt(batch -> batch.getCurrentCount() == null ? 0 : batch.getCurrentCount()).sum();
            for (BatchEvent event : eventMapper.selectList(Wrappers.<BatchEvent>lambdaQuery()
                    .in(BatchEvent::getFarmId, farmIds).eq(BatchEvent::getStatus, "EFFECTIVE")
                    .gt(BatchEvent::getEventDate, date).le(BatchEvent::getEventDate, BeijingTime.today())
                    .in(BatchEvent::getEventType, "ENTRY", "DEATH", "SALE"))) {
                int quantity = event.getQuantity() == null ? 0 : event.getQuantity();
                stock += "ENTRY".equals(event.getEventType()) ? -quantity : quantity;
            }
            return Math.max(0, stock);
        });
    }

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
                batch.getCategoryId(), batch.getEntryDate(), batch.getCurrentCount(), batch.getStatus(), penIds,
                batch.getEntryAgeDays(), batch.getEntryCount(), batch.getDeathCount(), batch.getSoldCount(),
                batch.getEntryPenId());
    }
}
