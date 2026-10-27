package com.zhimu.server.biz.herd.service;

import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchTraceMapper;
import com.zhimu.server.biz.herd.support.BatchAccess;
import com.zhimu.server.biz.herd.support.BatchQrLinks;
import com.zhimu.server.biz.herd.support.HerdErrors;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.biz.herd.support.QrCodes;
import com.zhimu.server.biz.herd.vo.BatchTraceVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BatchTraceService {
    private final BatchMapper batches;
    private final BatchTraceMapper traces;
    private final HerdSupport support;
    private final FarmLookup farms;
    private final BatchAccess access;
    private final BatchQrLinks links;

    public BatchTraceVO read(String token) {
        if (token == null || QrCodes.parseToken(QrCodes.DEFAULT_PREFIX + token) == null) throw HerdErrors.notFound();
        return TenantSkip.call(() -> {
            Batch batch = batches.selectByQrTokenIncludingDeleted(token);
            if (batch == null || !token.equals(batch.getQrToken()) || Boolean.TRUE.equals(batch.getDeleted())
                    || !Boolean.TRUE.equals(batch.getTraceEnabled()))
                throw HerdErrors.notFound();
            var farm = farms.findFarm(batch.getFarmId());
            if (farm == null || !"APPROVED".equals(farm.getAuditStatus()) || Boolean.TRUE.equals(farm.getDisabled())
                    || Boolean.TRUE.equals(farm.getDeleted()) || Boolean.TRUE.equals(farm.getFrozen()))
                throw HerdErrors.notFound();
            BatchTraceVO result = new BatchTraceVO();
            result.setBatchNo(batch.getBatchNo());
            result.setFarmName(farm.getName());
            result.setCategoryName(support.categoryName(batch.getCategoryId()));
            result.setBreedName(support.breedName(batch.getBreedId()));
            result.setSource(batch.getSource());
            result.setStatus(batch.getStatus());
            result.setEntryDate(batch.getEntryDate());
            result.setEndDate(batch.getEndDate());
            result.setEntryAgeDays(batch.getEntryAgeDays());
            result.setEntryAvgWeight(batch.getEntryAvgWeight());
            result.setUpdatedAt(batch.getUpdatedAt());
            var events = traces.events(batch.getFarmId(), batch.getId());
            if (events == null) events = java.util.List.of();
            result.setEvents(events);
            for (var event : events) {
                if (event.getUpdatedAt() != null && (result.getUpdatedAt() == null || event.getUpdatedAt().isAfter(result.getUpdatedAt())))
                    result.setUpdatedAt(event.getUpdatedAt());
            }
            return result;
        });
    }

    @Transactional
    public Settings settings(Long batchId, Settings request) {
        Batch batch = access.require(batchId);
        if (request == null || request.enabled() == null || request.version() == null) throw HerdErrors.paramInvalid("缺少溯源设置或版本号");
        if (!request.version().equals(batch.getVersion())) throw HerdErrors.versionConflict();
        batch.setTraceEnabled(request.enabled());
        if (Boolean.TRUE.equals(request.rotate())) batch.setQrToken(QrCodes.nextToken());
        if (batches.updateById(batch) == 0) throw HerdErrors.versionConflict();
        return new Settings(batch.getTraceEnabled(), false, batch.getVersion(), links.content(batch.getQrToken()));
    }

    public record Settings(Boolean enabled, Boolean rotate, Integer version, String qrContent) {}
}
