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

    @Override
    public Map<Long, WithdrawalStatus> statusesOfFarm(Long farmId, LocalDate date) {
        if (farmId == null || date == null) {
            return Map.of();
        }
        Map<Long, MedicationRecord> decisive = new LinkedHashMap<>();
        for (MedicationRecord record : recordMapper.selectList(Wrappers.<MedicationRecord>lambdaQuery()
                .eq(MedicationRecord::getFarmId, farmId)
                .gt(MedicationRecord::getWithdrawalEndDate, date))) {
            LocalDate started = record.getFirstDate() != null ? record.getFirstDate() : record.getLastDate();
            if (record.getBatchId() == null || record.getWithdrawalEndDate() == null
                    || !record.getWithdrawalEndDate().isAfter(date) || (started != null && started.isAfter(date))) {
                continue;
            }
            decisive.merge(record.getBatchId(), record, (first, second) ->
                    second.getWithdrawalEndDate().isAfter(first.getWithdrawalEndDate()) ? second : first);
        }
        Map<Long, WithdrawalStatus> result = new LinkedHashMap<>();
        decisive.forEach((batchId, record) -> result.put(batchId, new WithdrawalStatus(batchId,
                record.getWithdrawalEndDate(), record.getDrugName(), record.getLastDate(),
                record.getWithdrawalDays())));
        return result;
    }
}
