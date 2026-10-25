package com.zhimu.server.biz.finance.support;

import com.zhimu.server.biz.finance.mapper.ReportFeedQueryMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportFeedMetricsTest {

    private final ReportFeedQueryMapper mapper = mock(ReportFeedQueryMapper.class);
    private final ReportFeedMetrics metrics = new ReportFeedMetrics(mapper);
    private final List<Long> farms = List.of(1001L);
    private final LocalDate start = LocalDate.of(2026, 10, 19);

    @Test
    void comparesActualFeedInMatchingPeriods() {
        when(mapper.sumActualKg(farms, start, start.plusDays(6))).thenReturn(new BigDecimal("150.00"));
        when(mapper.sumActualKg(farms, start.minusDays(7), start.minusDays(1))).thenReturn(new BigDecimal("100.00"));
        var result = metrics.between(farms, start, start.plusDays(6), start.minusDays(7), start.minusDays(1));
        assertThat(result.getKg()).isEqualByComparingTo("150.00");
        assertThat(result.getComparable()).isTrue();
        assertThat(result.getChangeRate()).isEqualByComparingTo("0.5000");
    }

    @Test
    void missingPreviousActivityHasNoInventedComparison() {
        when(mapper.sumActualKg(farms, start, start)).thenReturn(new BigDecimal("25.00"));
        var result = metrics.between(farms, start, start, start.minusDays(1), start.minusDays(1));
        assertThat(result.getKg()).isEqualByComparingTo("25.00");
        assertThat(result.getComparable()).isFalse();
        assertThat(result.getChangeRate()).isNull();
    }
}
