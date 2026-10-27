package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.audit.service.AuditPendingService;
import com.zhimu.server.admin.system.mapper.readonly.AdminOverviewQueryMapper;
import com.zhimu.server.admin.system.query.CategoryDistRow;
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
import static org.mockito.Mockito.verify;
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

    @Test
    void 地图筛选把品类编号传给查询() {
        when(mapper.selectFarmMap(eq("PROVINCE"), eq(null), eq("PIG"), eq(101L))).thenReturn(List.of());
        FarmMapQuery query = new FarmMapQuery();
        query.setCategoryGroup("PIG");
        query.setCategoryId(101L);

        service.farmMap(query);

        verify(mapper).selectFarmMap("PROVINCE", null, "PIG", 101L);
    }

    @Test
    void 地区明细按品类过滤并校验品类编号() {
        when(mapper.selectRegionCounts("15", 101L)).thenReturn(null);
        when(mapper.selectRegionName("15")).thenReturn("内蒙古自治区");
        when(mapper.selectPendingFarms("15", 101L)).thenReturn(List.of());

        var detail = service.regionDetail("15", 101L);

        assertThat(detail.getRegionCode()).isEqualTo("15");
        assertThat(detail.getRegionName()).isEqualTo("内蒙古自治区");
        verify(mapper).selectRegionCounts("15", 101L);
        verify(mapper).selectPendingFarms("15", 101L);

        assertThatThrownBy(() -> service.regionDetail("15", 0L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40001));
    }

    @Test
    void 品类分布按品类名称返回并计算占比() {
        CategoryDistRow pig = new CategoryDistRow();
        pig.setCategoryId(101L);
        pig.setCategoryName("育肥猪");
        pig.setSpecies("PIG");
        pig.setCount(3L);
        CategoryDistRow chicken = new CategoryDistRow();
        chicken.setCategoryId(201L);
        chicken.setCategoryName("肉鸡");
        chicken.setSpecies("POULTRY");
        chicken.setCount(1L);
        when(mapper.selectCategoryDistribution()).thenReturn(List.of(pig, chicken));
        when(mapper.selectFarmStats()).thenReturn(null);
        when(mapper.selectUserStats(org.mockito.ArgumentMatchers.any())).thenReturn(null);
        when(mapper.selectActiveStats(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(null);
        when(mapper.selectMonthAudit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        var overview = service.overview();

        assertThat(overview.getCategoryDist()).hasSize(2);
        assertThat(overview.getCategoryDist().get(0).getCategoryId()).isEqualTo(101L);
        assertThat(overview.getCategoryDist().get(0).getCategoryName()).isEqualTo("育肥猪");
        assertThat(overview.getCategoryDist().get(0).getSpeciesText()).isEqualTo("猪");
        assertThat(overview.getCategoryDist().get(0).getRatio()).isEqualByComparingTo("0.7500");
        assertThat(overview.getCategoryDist().get(1).getRatio()).isEqualByComparingTo("0.2500");
    }
}
