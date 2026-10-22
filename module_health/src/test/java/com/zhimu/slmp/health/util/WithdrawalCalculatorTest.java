package com.zhimu.slmp.health.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 休药期计算：ZM2610-031 氟苯尼考粉 10月5日起 5 天（末次 10月9日）、休药 20 天 → 10月29日；
 * 阿莫西林可溶性粉末次 10月5日、休药 14 天 → 10月19日；批次取最晚的 10月29日，10月19日剩 10 天、进度 50%。
 */
public class WithdrawalCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    private final WithdrawalCalculator.DrugUse florfenicol = new WithdrawalCalculator.DrugUse(
            WithdrawalCalculator.lastDate(LocalDate.of(2026, 10, 5), 5), 20);
    private final WithdrawalCalculator.DrugUse amoxicillin = new WithdrawalCalculator.DrugUse(
            WithdrawalCalculator.lastDate(LocalDate.of(2026, 10, 1), 5), 14);

    @Test
    public void lastDateIsFirstDatePlusCourseMinusOne() {
        assertEquals(LocalDate.of(2026, 10, 9), WithdrawalCalculator.lastDate(LocalDate.of(2026, 10, 5), 5));
        assertEquals(LocalDate.of(2026, 10, 23), WithdrawalCalculator.lastDate(LocalDate.of(2026, 10, 19), 5));
        // 疗程 1 天：末次即首次
        assertEquals(LocalDate.of(2026, 10, 19), WithdrawalCalculator.lastDate(LocalDate.of(2026, 10, 19), 1));
    }

    @Test
    public void releaseDateTakesLatestDrug() {
        assertEquals(LocalDate.of(2026, 10, 29), florfenicol.endDate());
        assertEquals(LocalDate.of(2026, 10, 19), amoxicillin.endDate());
        List<WithdrawalCalculator.DrugUse> uses = Arrays.asList(amoxicillin, florfenicol);
        assertEquals(LocalDate.of(2026, 10, 29), WithdrawalCalculator.batchReleaseDate(uses));
        assertSame(florfenicol, WithdrawalCalculator.decisive(uses));
        assertNull(WithdrawalCalculator.batchReleaseDate(Collections.emptyList()));
    }

    @Test
    public void statusDaysLeftAndProgress() {
        LocalDate release = LocalDate.of(2026, 10, 29);
        assertEquals(WithdrawalCalculator.STATUS_IN_WITHDRAWAL, WithdrawalCalculator.status(release, TODAY));
        assertEquals(10, WithdrawalCalculator.daysLeft(release, TODAY));
        assertEquals(0.5, WithdrawalCalculator.progress(florfenicol.lastDate, 20, TODAY), 1e-9);
        // 可出栏日期当天即已到期，进度满格
        assertEquals(WithdrawalCalculator.STATUS_ENDED, WithdrawalCalculator.status(release, release));
        assertEquals(0, WithdrawalCalculator.daysLeft(release, release.plusDays(3)));
        assertEquals(1.0, WithdrawalCalculator.progress(florfenicol.lastDate, 20, release.plusDays(5)), 1e-9);
        // 没有用药记录
        assertEquals(WithdrawalCalculator.STATUS_NONE, WithdrawalCalculator.status(null, TODAY));
    }

    @Test
    public void tagsLatestAndEnded() {
        WithdrawalCalculator.DrugUse decisive = WithdrawalCalculator.decisive(Arrays.asList(florfenicol, amoxicillin));
        assertEquals("LATEST", WithdrawalCalculator.tagOf(florfenicol, decisive, TODAY));
        assertEquals("ENDED", WithdrawalCalculator.tagOf(amoxicillin, decisive, TODAY));
        // 休药结束后决定项也显示已到期
        assertEquals("ENDED", WithdrawalCalculator.tagOf(florfenicol, decisive, LocalDate.of(2026, 10, 30)));
    }
}
