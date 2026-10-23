package com.zhimu.server.biz.health.support;

import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 模块08 的日期与浓度口径（2.7 节，测试固定今天 2026-10-19）。 */
class HealthFormatTest {

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), BeijingTime.ZONE));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void dateTextAndWeekFollowBeijingToday() {
        assertThat(HealthFormat.dateText(LocalDate.of(2026, 10, 19))).isEqualTo("今天");
        assertThat(HealthFormat.dateText(LocalDate.of(2026, 10, 20))).isEqualTo("明天");
        assertThat(HealthFormat.dateText(LocalDate.of(2026, 10, 12))).isEqualTo("10月12日");
        assertThat(HealthFormat.dateWithWeek(LocalDate.of(2026, 11, 6))).isEqualTo("11月6日 周五");
        assertThat(HealthFormat.rangeText(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9)))
                .isEqualTo("10月5日–10月9日");
    }

    @Test
    void concentrationComparesByValue() {
        assertThat(HealthFormat.concentrationValue("1:500")).isEqualTo(500);
        assertThat(HealthFormat.concentrationValue("1：500")).isNull();
        assertThat(HealthFormat.sameConcentration("1:500", "1:500")).isTrue();
        assertThat(HealthFormat.sameConcentration("1:500", "1:300")).isFalse();
    }

    @Test
    void statusTextAndUnitTextMatchAppMock() {
        assertThat(HealthCodes.immuneStatusText("PENDING")).isEqualTo("待接种");
        assertThat(HealthCodes.immuneStatusText("MISSED")).isEqualTo("漏免");
        assertThat(HealthCodes.taskStatusText("OVERDUE")).isEqualTo("已逾期");
        assertThat(HealthCodes.disinfectPlanStatusText("PAUSED")).isEqualTo("已暂停");
        assertThat(HealthCodes.withdrawalStatusText("IN_WITHDRAWAL")).isEqualTo("休药中");
        assertThat(HealthCodes.doseUnitText("DOSE_PER_HEAD")).isEqualTo("头份 / 头");
        assertThat(HealthCodes.drugDoseUnitText("G_PER_L_WATER")).isEqualTo("克 / 升水");
        assertThat(HealthCodes.unitText("L")).isEqualTo("升");
        assertThat(HealthCodes.routeText("MIX_FEED")).isEqualTo("混饲");
    }
}
