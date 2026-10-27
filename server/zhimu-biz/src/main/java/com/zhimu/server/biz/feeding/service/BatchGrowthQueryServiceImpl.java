package com.zhimu.server.biz.feeding.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.feeding.api.BatchGrowthQueryService;
import com.zhimu.server.biz.feeding.entity.BatchGrowth;
import com.zhimu.server.biz.feeding.mapper.BatchGrowthMapper;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** 复用称重登记同步维护的每批次生长汇总，避免关联列表逐批扫描历史称重。 */
@Service
@RequiredArgsConstructor
public class BatchGrowthQueryServiceImpl implements BatchGrowthQueryService {

    private final BatchGrowthMapper growthMapper;

    @Override
    public Map<Long, LatestWeight> latestWeights(Long farmId, Collection<Long> batchIds) {
        if (farmId == null || batchIds == null || batchIds.isEmpty()) {
            return Map.of();
        }
        var rows = TenantSkip.call(() -> growthMapper.selectList(Wrappers.<BatchGrowth>lambdaQuery()
                .eq(BatchGrowth::getFarmId, farmId)
                .in(BatchGrowth::getBatchId, batchIds)
                .isNotNull(BatchGrowth::getLastWeighId)
                .le(BatchGrowth::getLastWeighDate, BeijingTime.today())
                .gt(BatchGrowth::getLastAvgWeight, 0)));
        Map<Long, LatestWeight> result = new LinkedHashMap<>();
        for (BatchGrowth row : rows) {
            result.put(row.getBatchId(), new LatestWeight(row.getLastAvgWeight(), row.getLastWeighDate()));
        }
        return result;
    }
}
