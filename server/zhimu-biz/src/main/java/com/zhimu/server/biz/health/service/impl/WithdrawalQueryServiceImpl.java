package com.zhimu.server.biz.health.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.health.api.WithdrawalQueryService;
import com.zhimu.server.biz.health.entity.MedicationRecord;
import com.zhimu.server.biz.health.mapper.MedicationRecordMapper;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** 按养殖场汇总有效用药记录，与 D17 和首页采用同一口径。 */
@Service
@RequiredArgsConstructor
public class WithdrawalQueryServiceImpl implements WithdrawalQueryService {

    private final MedicationRecordMapper recordMapper;

    @Override
    public Map<Long, LocalDate> releaseDatesOfFarm(Long farmId) {
        if (farmId == null) {
            return Map.of();
        }
        Map<Long, LocalDate> result = new LinkedHashMap<>();
        for (MedicationRecord record : recordMapper.selectList(Wrappers.<MedicationRecord>lambdaQuery()
                .eq(MedicationRecord::getFarmId, farmId)
                .gt(MedicationRecord::getWithdrawalEndDate, BeijingTime.today()))) {
            if (record.getBatchId() != null) {
                result.merge(record.getBatchId(), record.getWithdrawalEndDate(),
                        (first, second) -> first.isAfter(second) ? first : second);
            }
        }
        return result;
    }
}
