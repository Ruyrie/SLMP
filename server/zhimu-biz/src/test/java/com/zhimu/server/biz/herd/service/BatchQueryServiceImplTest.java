package com.zhimu.server.biz.herd.service;

import com.zhimu.server.biz.herd.HerdTableInfo;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchEvent;
import com.zhimu.server.biz.herd.mapper.BatchEventMapper;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.biz.herd.service.impl.BatchQueryServiceImpl;
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

class BatchQueryServiceImplTest {

    private final BatchMapper batches = mock(BatchMapper.class);
    private final BatchEventMapper events = mock(BatchEventMapper.class);
    private final BatchQueryServiceImpl service = new BatchQueryServiceImpl(batches, mock(BatchPenMapper.class), events);

    @BeforeEach
    void setUp() {
        HerdTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T01:00:00Z"), BeijingTime.ZONE));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void historicalStockReversesLaterEntriesSalesAndDeaths() {
        Batch batch = new Batch();
        batch.setCurrentCount(80);
        when(batches.selectList(any())).thenReturn(List.of(batch));
        when(events.selectList(any())).thenReturn(List.of(event("ENTRY", 30), event("SALE", 20), event("DEATH", 10)));

        assertThat(service.stockCountAt(List.of(1001L), LocalDate.of(2026, 10, 12))).isEqualTo(80);
    }

    @Test
    void deathsAreSummedWithoutTreatingMissingQuantityAsAnError() {
        when(events.selectList(any())).thenReturn(List.of(event("DEATH", 3), event("DEATH", 7), event("DEATH", null)));

        assertThat(service.deathCount(List.of(1001L), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 19))).isEqualTo(10);
        assertThat(service.deathCount(List.of(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 19))).isZero();
    }

    private BatchEvent event(String type, Integer quantity) {
        BatchEvent result = new BatchEvent();
        result.setEventType(type);
        result.setQuantity(quantity);
        return result;
    }
}
