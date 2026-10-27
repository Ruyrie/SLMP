package com.zhimu.server.admin.system.service;

import com.zhimu.server.admin.system.mapper.UserActivityStatMapper;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserActivityStatServiceTest {
    private final UserActivityStatMapper mapper = mock(UserActivityStatMapper.class);
    private final UserActivityStatService service = new UserActivityStatService(mapper);

    @BeforeEach
    void clock() {
        // UTC 仍是 26 日，业务时间已是 27 日。
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-26T17:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @AfterEach
    void reset() { BeijingTime.setClock(null); }

    @Test
    void writesAuthenticatedUserInBusinessTimezoneAndUsesZeroWithoutFarm() {
        service.touch(606001L, null);
        verify(mapper).touch(BeijingTime.today(), 606001L, 0L, BeijingTime.now());
        service.touch(606002L, 60601L);
        verify(mapper).touch(BeijingTime.today(), 606002L, 60601L, BeijingTime.now());
    }

    @Test
    void ignoresAnonymousAndSystemUsers() {
        service.touch(null, 60601L);
        service.touch(0L, 60601L);
        service.touch(-1L, 60601L);
        verifyNoInteractions(mapper);
    }

    @Test
    void rebuildsEveryDayOfThirtyCompletedDaysPlusCurrentDay() {
        service.rebuildRecent();
        LocalDate end = LocalDate.of(2026, 10, 27);
        var ordered = inOrder(mapper);
        ordered.verify(mapper).backfillActivity(end.minusDays(30), end);
        ordered.verify(mapper).resetProduction(end.minusDays(30), end);
        ordered.verify(mapper).aggregateProduction(end.minusDays(30), end);
        ArgumentCaptor<LocalDate> dates = ArgumentCaptor.forClass(LocalDate.class);
        ordered.verify(mapper, times(31)).aggregatePlatformDay(dates.capture());
        assertThat(dates.getAllValues()).containsExactlyElementsOf(end.minusDays(30).datesUntil(end.plusDays(1)).toList());
    }

    @Test
    void failingAggregateStopsPublicationAndPropagatesForTransactionRollback() {
        doThrow(new IllegalStateException("database error")).when(mapper).aggregateProduction(any(), any());
        assertThatThrownBy(service::rebuildRecent).isInstanceOf(IllegalStateException.class);
        verify(mapper, never()).aggregatePlatformDay(any());
    }
}
