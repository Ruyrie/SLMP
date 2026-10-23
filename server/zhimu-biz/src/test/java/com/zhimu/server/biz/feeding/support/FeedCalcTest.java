package com.zhimu.server.biz.feeding.support;

import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.herd.api.BatchInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 任务生成、计划量、偏差、餐次与逾期口径。 */
class FeedCalcTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @AfterEach
    void resetClock() {
        com.zhimu.server.common.util.BeijingTime.setClock(null);
    }

    @Test
    void dayAgeAndGenerationDayAge() {
        BatchInfo batch = batch(LocalDate.of(2026, 10, 1), 78, 200);
        assertThat(FeedCalc.dayAge(batch, LocalDate.of(2026, 10, 19))).isEqualTo(96);
        assertThat(FeedCalc.generationDayAge(batch, LocalDate.of(2026, 10, 19))).isEqualTo(95);
        assertThat(FeedCalc.generationDayAge(batch, LocalDate.of(2026, 10, 1))).isEqualTo(78);
    }

    @Test
    void planQtyAndDeviation() {
        assertThat(FeedCalc.planQty(new BigDecimal("2.4"), 200, 3)).isEqualByComparingTo("160.00");
        assertThat(FeedCalc.planQty(new BigDecimal("2.2"), 150, 3)).isEqualByComparingTo("110.00");
        assertThat(FeedCalc.deviation(new BigDecimal("140"), new BigDecimal("160"))).isEqualByComparingTo("-0.1250");
        assertThat(FeedCalc.deviation(new BigDecimal("158"), new BigDecimal("160"))).isEqualByComparingTo("-0.0125");
    }

    @Test
    void abnormalTypeUsesStrictlyGreaterThanThreshold() {
        assertThat(FeedCalc.abnormalType(new BigDecimal("-0.1250"), new BigDecimal("0.1000"))).isEqualTo("UNDER");
        assertThat(FeedCalc.abnormalType(new BigDecimal("0.1250"), new BigDecimal("0.1000"))).isEqualTo("OVER");
        assertThat(FeedCalc.abnormalType(new BigDecimal("0.1000"), new BigDecimal("0.1000"))).isEqualTo("NONE");
        assertThat(FeedCalc.abnormalType(new BigDecimal("0.0000"), new BigDecimal("0.1000"))).isEqualTo("NONE");
    }

    @Test
    void mealNamesByTimeAndDuplicateBecomesExtra() {
        List<FeedCalc.MealSlot> slots = FeedCalc.mealSlots(List.of("07:00", "11:30", "17:00"));
        assertThat(slots).extracting(FeedCalc.MealSlot::mealName).containsExactly("早餐", "午餐", "晚餐");
        assertThat(slots).extracting(FeedCalc.MealSlot::mealCode)
                .containsExactly("BREAKFAST", "LUNCH", "DINNER");
        List<FeedCalc.MealSlot> extra = FeedCalc.mealSlots(List.of("07:00", "08:00"));
        assertThat(extra).extracting(FeedCalc.MealSlot::mealName).containsExactly("早餐", "加餐");
        assertThat(extra).extracting(FeedCalc.MealSlot::mealNo).containsExactly(1, 2);
    }

    @Test
    void effectiveStatusTurnsPendingIntoOverdueAfterDeadline() {
        com.zhimu.server.common.util.BeijingTime.setClock(Clock.fixed(
                Instant.parse("2026-10-19T01:30:00Z"), ZONE));
        FeedTask task = new FeedTask();
        task.setStatus("PENDING");
        task.setFeedAt(LocalDateTime.of(2026, 10, 19, 7, 0));
        task.setDeadlineAt(LocalDateTime.of(2026, 10, 19, 9, 0));
        assertThat(FeedCalc.effectiveStatus(task, LocalDateTime.of(2026, 10, 19, 9, 30))).isEqualTo("OVERDUE");
        assertThat(FeedCalc.effectiveStatus(task, LocalDateTime.of(2026, 10, 19, 8, 59))).isEqualTo("PENDING");
        assertThat(FeedCalc.overdueMinutes(task, LocalDateTime.of(2026, 10, 19, 9, 30))).isEqualTo(150);
    }

    @Test
    void parseTimeAcceptsHourMinute() {
        assertThat(FeedCalc.parseTime("07:00")).isEqualTo(LocalTime.of(7, 0));
        assertThat(FeedCalc.parseTime("7:00")).isEqualTo(LocalTime.of(7, 0));
        assertThat(FeedCalc.parseTime("bad")).isNull();
    }

    private static BatchInfo batch(LocalDate entryDate, int entryAgeDays, int currentCount) {
        return new BatchInfo(1001L, 3001L, "ZM2610-031", 1101L, 101L, entryDate, currentCount, "ACTIVE",
                List.of(2003L), entryAgeDays, 205, 5, 0, 2003L);
    }
}
