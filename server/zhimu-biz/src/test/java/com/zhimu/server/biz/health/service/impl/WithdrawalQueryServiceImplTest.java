package com.zhimu.server.biz.health.service.impl;

import com.zhimu.server.biz.health.HealthTableInfo;
import com.zhimu.server.biz.health.api.WithdrawalQueryService;
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

    @Test
    void statusOnADateUsesTheDecisiveDrugAndIgnoresMedicationStartedLater() {
        var records = mock(MedicationRecordMapper.class);
        MedicationRecord early = medication(3001L, "2026-10-27");
        early.setDrugName("阿莫西林可溶性粉");
        early.setFirstDate(LocalDate.of(2026, 10, 10));
        early.setLastDate(LocalDate.of(2026, 10, 13));
        early.setWithdrawalDays(14);
        MedicationRecord decisive = medication(3001L, "2026-11-06");
        decisive.setDrugName("氟苯尼考粉");
        decisive.setFirstDate(LocalDate.of(2026, 10, 15));
        decisive.setLastDate(LocalDate.of(2026, 10, 17));
        decisive.setWithdrawalDays(20);
        when(records.selectList(any())).thenReturn(List.of(early, decisive));
        var service = new WithdrawalQueryServiceImpl(records);

        WithdrawalQueryService.WithdrawalStatus today = service.statusOf(1001L, 3001L, LocalDate.of(2026, 10, 19));
        assertThat(today).isEqualTo(new WithdrawalQueryService.WithdrawalStatus(3001L, LocalDate.of(2026, 11, 6),
                "氟苯尼考粉", LocalDate.of(2026, 10, 17), 20));

        // 10-12 只用了阿莫西林（氟苯尼考 10-15 才开始）：按当天已用的药判断
        WithdrawalQueryService.WithdrawalStatus backdated =
                service.statusOf(1001L, 3001L, LocalDate.of(2026, 10, 12));
        assertThat(backdated.releaseDate()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(backdated.drugName()).isEqualTo("阿莫西林可溶性粉");

        // 首次用药前不在休药期
        assertThat(service.statusOf(1001L, 3001L, LocalDate.of(2026, 10, 9))).isNull();
        assertThat(service.statusOf(1001L, 3002L, LocalDate.of(2026, 10, 19))).isNull();
    }

    private MedicationRecord medication(Long batch, String release) {
        MedicationRecord result = new MedicationRecord();
        result.setBatchId(batch);
        result.setWithdrawalEndDate(LocalDate.parse(release));
        return result;
    }
}
