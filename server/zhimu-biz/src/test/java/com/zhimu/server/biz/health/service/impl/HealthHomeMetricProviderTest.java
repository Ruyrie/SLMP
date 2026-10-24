package com.zhimu.server.biz.health.service.impl;

import com.zhimu.server.biz.health.HealthTableInfo;
import com.zhimu.server.biz.health.entity.MedicationRecord;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.mapper.MedicationRecordMapper;
import com.zhimu.server.biz.health.query.BatchPenStockRow;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthHomeMetricProviderTest {

    @BeforeEach
    void setUp() {
        HealthTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-24T02:00:00Z"), BeijingTime.ZONE));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void usesLatestReleaseAndCurrentPenInsteadOfMedicationOriginalPen() {
        var records = mock(MedicationRecordMapper.class);
        var references = mock(HealthReferenceMapper.class);
        MedicationRecord first = medication(3001L, "2026-10-29");
        MedicationRecord last = medication(3001L, "2026-11-01");
        MedicationRecord other = medication(3002L, "2026-11-04");
        when(records.selectList(any())).thenReturn(List.of(first, last, other));
        when(references.selectActiveBatchPens(1001L)).thenReturn(List.of(
                pen(3001L, 2005L), pen(3001L, 2006L), pen(3002L, 2007L)));
        var member = new FarmContext.FarmMember(1001L, "东山猪场", 10003L, 5003L,
                FarmRole.STAFF, Set.of(2005L, 2006L), FarmStatus.NORMAL);

        var result = new HealthHomeMetricProvider(records, references).withdrawalDaysByPen(member);

        assertThat(result).containsOnlyKeys(2005L, 2006L).containsEntry(2005L, 8).containsEntry(2006L, 8);
    }

    private static MedicationRecord medication(Long batchId, String release) {
        MedicationRecord result = new MedicationRecord();
        result.setBatchId(batchId);
        result.setPenId(2003L);
        result.setWithdrawalEndDate(LocalDate.parse(release));
        return result;
    }

    private static BatchPenStockRow pen(Long batchId, Long penId) {
        BatchPenStockRow result = new BatchPenStockRow();
        result.setBatchId(batchId);
        result.setPenId(penId);
        result.setStockCount(100);
        return result;
    }
}
