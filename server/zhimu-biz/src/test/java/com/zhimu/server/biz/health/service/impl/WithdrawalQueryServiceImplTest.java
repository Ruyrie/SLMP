package com.zhimu.server.biz.health.service.impl;

import com.zhimu.server.biz.health.HealthTableInfo;
import com.zhimu.server.biz.health.entity.MedicationRecord;
import com.zhimu.server.biz.health.mapper.MedicationRecordMapper;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WithdrawalQueryServiceImplTest {

    @BeforeEach
    void setUp() {
        HealthTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T01:00:00Z"), BeijingTime.ZONE));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void multipleDrugsUseTheLatestWithdrawalDateForEachBatch() {
        var records = mock(MedicationRecordMapper.class);
        when(records.selectList(any())).thenReturn(List.of(medication(3001L, "2026-10-27"),
                medication(3001L, "2026-10-29"), medication(3002L, "2026-10-22")));

        var dates = new WithdrawalQueryServiceImpl(records).releaseDatesOfFarm(1001L);

        assertThat(dates).hasSize(2).containsEntry(3001L, LocalDate.of(2026, 10, 29))
                .containsEntry(3002L, LocalDate.of(2026, 10, 22));
    }

    private MedicationRecord medication(Long batch, String release) {
        MedicationRecord result = new MedicationRecord();
        result.setBatchId(batch);
        result.setWithdrawalEndDate(LocalDate.parse(release));
        return result;
    }
}
