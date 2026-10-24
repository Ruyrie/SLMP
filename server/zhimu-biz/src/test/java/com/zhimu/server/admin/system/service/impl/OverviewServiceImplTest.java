package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.audit.service.AuditPendingService;
import com.zhimu.server.admin.system.mapper.readonly.AdminOverviewQueryMapper;
import com.zhimu.server.admin.system.query.DailyStatRow;
import com.zhimu.server.admin.system.query.FarmMapQuery;
import com.zhimu.server.admin.system.query.TrendQuery;
import com.zhimu.server.admin.system.service.OperationLogAdminService;
import com.zhimu.server.admin.system.vo.TrendVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OverviewServiceImplTest {

    private final AdminOverviewQueryMapper mapper = mock(AdminOverviewQueryMapper.class);
    private final OverviewServiceImpl service = new OverviewServiceImpl(mapper,
            mock(AuditPendingService.class), mock(OperationLogAdminService.class));

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:30:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void 趋势截至昨天_补齐30天并计算7日均值和汇总() {
        LocalDate end = LocalDate.of(2026, 10, 18);
        LocalDate start = end.minusDays(29);
        DailyStatRow row = new DailyStatRow();
        row.setStatDate(end);
        row.setValue(10L);
        when(mapper.selectPlatformDailyStats(eq("ACTIVE_USER"), eq(start), eq(end))).thenReturn(List.of(row));

        TrendQuery query = new TrendQuery();
        query.setMetric("ACTIVE_USER");
        TrendVO trend = service.trend(query);

        assertThat(trend.getRangeStart()).isEqualTo(start);
        assertThat(trend.getRangeEnd()).isEqualTo(end);
        assertThat(trend.getPoints()).hasSize(30);
        assertThat(trend.getPoints().get(29).getValue()).isEqualTo(10);
        assertThat(trend.getPoints().get(29).getAvg7()).isEqualByComparingTo("1.4");
        assertThat(trend.getSummary().getMaxDate()).isEqualTo(end);
        assertThat(trend.getSummary().getMaxValue()).isEqualTo(10);
        assertThat(trend.getSummary().getDailyAvg()).isEqualByComparingTo("0.3");
    }

    @Test
    void 日统计表为空时实时聚合() {
        LocalDate end = LocalDate.of(2026, 10, 18);
        LocalDate start = end.minusDays(29);
        DailyStatRow row = new DailyStatRow();
        row.setStatDate(end);
        row.setValue(7L);
        when(mapper.selectPlatformDailyStats("NEW_USER", start, end)).thenReturn(List.of());
        when(mapper.selectNewUserDailyStats(start, end)).thenReturn(List.of(row));

        TrendQuery query = new TrendQuery();
        query.setMetric("NEW_USER");
        TrendVO trend = service.trend(query);

        assertThat(trend.getPoints().get(29).getValue()).isEqualTo(7);
    }

    @Test
    void 城市层级缺少父区划码返回40001() {
        FarmMapQuery query = new FarmMapQuery();
        query.setLevel("CITY");
        assertThatThrownBy(() -> service.farmMap(query))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40001));
    }
}
