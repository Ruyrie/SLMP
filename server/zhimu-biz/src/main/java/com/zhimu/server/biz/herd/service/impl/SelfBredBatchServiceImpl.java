package com.zhimu.server.biz.herd.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.herd.api.SelfBredBatchService;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.biz.herd.mapper.PenMapper;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import com.zhimu.server.biz.herd.service.BatchService;
import com.zhimu.server.biz.herd.request.BatchCreateRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 自繁批次存栏调整（产仔 / 出雏修改申请回写）：与调用方同一事务，按差额原子调整 t_batch、
 * t_batch_pen 与 t_pen；任一在栏数将为负返回 42205「在栏数不足，无法按申请修改」。
 */
@Service
@RequiredArgsConstructor
public class SelfBredBatchServiceImpl implements SelfBredBatchService {

    private static final String MSG_STOCK_NOT_ENOUGH = "在栏数不足，无法按申请修改";

    private final BatchMapper batchMapper;
    private final BatchPenMapper batchPenMapper;
    private final PenMapper penMapper;
    private final ObjectProvider<BatchService> batchService;

    @Override
    @Transactional
    public CreatedBatch create(Long penId, Long breedId, int quantity, LocalDate date,
                               boolean capacityConfirmed, String sourceType, Long sourceId) {
        // 来源记录类型与 t_batch.source_record_type 一致：FARROWING 产仔 / INCUBATION 出雏（HATCH 为旧写法）
        if (sourceId == null || sourceType == null
                || !java.util.Set.of("FARROWING", "INCUBATION", "HATCH").contains(sourceType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "自繁批次来源不正确");
        }
        String recordType = "HATCH".equals(sourceType) ? "INCUBATION" : sourceType;
        BatchCreateRequest request = new BatchCreateRequest();
        request.setPenId(penId);
        request.setBreedId(breedId);
        request.setPurpose("COMMERCIAL");
        request.setSource("SELF_BRED");
        request.setEntryDate(date);
        request.setEntryAgeDays(0);
        request.setEntryCount(quantity);
        request.setCapacityConfirmed(capacityConfirmed);
        var result = batchService.getObject().create(request, "self-bred:" + recordType + ":" + sourceId);
        if (batchMapper.update(null, Wrappers.<Batch>lambdaUpdate()
                .eq(Batch::getId, result.getBatchId())
                .set(Batch::getSourceRecordType, recordType)
                .set(Batch::getSourceRecordId, sourceId)) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
        return new CreatedBatch(result.getBatchId(), result.getBatchNo());
    }

    @Override
    @Transactional
    public int adjustEntry(Long batchId, int countDelta, LocalDate entryDate) {
        if (batchId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "自繁批次不存在");
        }
        Batch batch = TenantSkip.call(() -> batchMapper.selectByIdIncludingDeleted(batchId));
        if (batch == null || Boolean.TRUE.equals(batch.getDeleted())) {
            throw new BizException(ErrorCode.NOT_FOUND, "自繁批次不存在");
        }
        int entryBefore = batch.getEntryCount() == null ? 0 : batch.getEntryCount();
        int currentBefore = batch.getCurrentCount() == null ? 0 : batch.getCurrentCount();
        int entryAfter = entryBefore + countDelta;
        int currentAfter = currentBefore + countDelta;
        if (entryAfter < 0 || currentAfter < 0) {
            throw new BizException(ErrorCode.COUNT_EXCEEDED, MSG_STOCK_NOT_ENOUGH);
        }
        LocalDateTime now = BeijingTime.now();
        LocalDate bizDate = entryDate == null ? BeijingTime.today() : entryDate;
        if (countDelta != 0) {
            Long penId = batch.getEntryPenId();
            if (penId == null) {
                throw new BizException(ErrorCode.COUNT_EXCEEDED, MSG_STOCK_NOT_ENOUGH);
            }
            if (countDelta > 0) {
                if (batchPenMapper.addStock(batchId, penId, countDelta, bizDate, now) == 0) {
                    BatchPen row = new BatchPen();
                    row.setFarmId(batch.getFarmId());
                    row.setBatchId(batchId);
                    row.setPenId(penId);
                    row.setStockCount(countDelta);
                    row.setFirstInDate(bizDate);
                    batchPenMapper.insert(row);
                }
            } else if (batchPenMapper.deduct(batchId, penId, -countDelta, bizDate, now) == 0) {
                throw new BizException(ErrorCode.COUNT_EXCEEDED, MSG_STOCK_NOT_ENOUGH);
            }
            if (penMapper.addStock(penId, countDelta, now) == 0) {
                throw new BizException(ErrorCode.COUNT_EXCEEDED, MSG_STOCK_NOT_ENOUGH);
            }
        }
        int version = batch.getVersion() == null ? 0 : batch.getVersion();
        int rows = batchMapper.update(null, Wrappers.<Batch>lambdaUpdate()
                .eq(Batch::getId, batchId)
                .eq(Batch::getVersion, version)
                .set(Batch::getEntryCount, entryAfter)
                .set(Batch::getCurrentCount, currentAfter)
                .set(entryDate != null, Batch::getEntryDate, entryDate)
                .set(Batch::getVersion, version + 1)
                .set(Batch::getUpdatedAt, now));
        if (rows == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
        return currentAfter;
    }
}
