package com.zhimu.server.biz.feeding.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.StdQueryService;
import com.zhimu.server.admin.base.vo.BreedStandardsVO;
import com.zhimu.server.biz.feeding.api.BatchGrowthQueryService;
import com.zhimu.server.biz.feeding.entity.BatchGrowth;
import com.zhimu.server.biz.feeding.entity.WeighRecord;
import com.zhimu.server.biz.feeding.mapper.BatchGrowthMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordMapper;
import com.zhimu.server.biz.feeding.support.GrowthCalc;
import com.zhimu.server.biz.feeding.vo.WeighVO;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 复用称重登记同步维护的每批次生长汇总，避免关联列表逐批扫描历史称重。 */
@Service
@RequiredArgsConstructor
public class BatchGrowthQueryServiceImpl implements BatchGrowthQueryService {

    private final BatchGrowthMapper growthMapper;
    private final ObjectProvider<WeighRecordMapper> recordMapper;
    private final ObjectProvider<StdQueryService> stdQueryService;

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

    @Override
    public WeighSnapshot latestWeighing(Long farmId, Long batchId, Long breedId, Integer currentDayAge) {
        List<WeighVO.StdPoint> points = standards(breedId);
        BigDecimal currentStd = GrowthCalc.interpolate(points, currentDayAge);
        WeighRecordMapper mapper = recordMapper == null ? null : recordMapper.getIfAvailable();
        WeighRecord latest = null;
        if (mapper != null && farmId != null && batchId != null) {
            List<WeighRecord> records = TenantSkip.call(() -> mapper.selectList(Wrappers.<WeighRecord>lambdaQuery()
                    .eq(WeighRecord::getFarmId, farmId)
                    .eq(WeighRecord::getBatchId, batchId)
                    .le(WeighRecord::getWeighDate, BeijingTime.today())
                    .gt(WeighRecord::getAvgWeight, 0)
                    .orderByDesc(WeighRecord::getWeighDate)
                    .orderByDesc(WeighRecord::getId)
                    .last("LIMIT 1")));
            latest = records.isEmpty() ? null : records.get(0);
        }
        if (latest == null) {
            return new WeighSnapshot(null, null, null, null, null, currentStd);
        }
        BigDecimal std = latest.getStdWeight() != null ? latest.getStdWeight()
                : GrowthCalc.interpolate(points, latest.getDayAge());
        BigDecimal deviation = latest.getStdWeight() != null && latest.getDeviationRatio() != null
                ? latest.getDeviationRatio() : GrowthCalc.deviation(latest.getAvgWeight(), std);
        return new WeighSnapshot(latest.getAvgWeight(), latest.getWeighDate(), latest.getDayAge(), std, deviation,
                currentStd);
    }

    private List<WeighVO.StdPoint> standards(Long breedId) {
        StdQueryService query = stdQueryService == null ? null : stdQueryService.getIfAvailable();
        if (query == null || breedId == null) {
            return List.of();
        }
        try {
            BreedStandardsVO standards = query.standards(breedId, null);
            if (standards == null || standards.getWeight() == null || standards.getWeight().getPoints() == null) {
                return List.of();
            }
            List<WeighVO.StdPoint> points = new ArrayList<>();
            for (BreedStandardsVO.WeightPointVO point : standards.getWeight().getPoints()) {
                if (point.getDayAge() == null || point.getStdWeight() == null) {
                    continue;
                }
                WeighVO.StdPoint vo = new WeighVO.StdPoint();
                vo.setDayAge(point.getDayAge());
                vo.setStdWeight(point.getStdWeight());
                points.add(vo);
            }
            return points;
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
