package com.zhimu.server.biz.health.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.health.entity.MedicationRecord;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.mapper.MedicationRecordMapper;
import com.zhimu.server.biz.today.api.HomeMetricProvider;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 首页休药状态与 D17 共用有效用药记录口径；按当前在栏分布展示，转栏后也能正确更新。 */
@Component
@RequiredArgsConstructor
public class HealthHomeMetricProvider implements HomeMetricProvider {

    private final MedicationRecordMapper recordMapper;
    private final HealthReferenceMapper referenceMapper;

    @Override
    public String moduleName() {
        return "health";
    }

    @Override
    public Map<Long, Integer> withdrawalDaysByPen(FarmContext.FarmMember member) {
        if (member == null || member.farmId() == null) {
            return Map.of();
        }
        LocalDate today = BeijingTime.today();
        Map<Long, LocalDate> releaseByBatch = releaseByBatch(member.farmId(), today);
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (var pen : referenceMapper.selectActiveBatchPens(member.farmId())) {
            if (member.isStaff() && !member.canAccessPen(pen.getPenId())) {
                continue;
            }
            LocalDate release = releaseByBatch.get(pen.getBatchId());
            if (release != null) {
                int days = Math.toIntExact(ChronoUnit.DAYS.between(today, release));
                result.merge(pen.getPenId(), days, Math::max);
            }
        }
        return result;
    }

    @Override
    public int withdrawalBatchCount(Long farmId) {
        if (farmId == null) {
            return 0;
        }
        return releaseByBatch(farmId, BeijingTime.today()).size();
    }

    @Override
    public LocalDate nearestWithdrawalReleaseDate(Long farmId) {
        if (farmId == null) {
            return null;
        }
        return releaseByBatch(farmId, BeijingTime.today()).values().stream().min(LocalDate::compareTo).orElse(null);
    }

    private Map<Long, LocalDate> releaseByBatch(Long farmId, LocalDate today) {
        Map<Long, LocalDate> releaseByBatch = new HashMap<>();
        for (MedicationRecord record : recordMapper.selectList(Wrappers.<MedicationRecord>lambdaQuery()
                .eq(MedicationRecord::getFarmId, farmId)
                .gt(MedicationRecord::getWithdrawalEndDate, today))) {
            releaseByBatch.merge(record.getBatchId(), record.getWithdrawalEndDate(),
                    (first, second) -> first.isAfter(second) ? first : second);
        }
        return releaseByBatch;
    }
}
