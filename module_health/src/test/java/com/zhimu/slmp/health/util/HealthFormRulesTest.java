package com.zhimu.slmp.health.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zhimu.slmp.health.TestRules;

import org.junit.Rule;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 接种表单与通用校验（今天 2026-10-19）：剂量、接种数量不超过存栏、接种日期区间、接种日龄、调整日期与原因、
 * 漏免 7 天、浓度格式与比较、消毒用量不超过结存。
 */
public class HealthFormRulesTest {

    @Rule
    public TestRules rules = new TestRules();

    @Test
    public void doseMustBePositiveWithThreeDecimals() {
        assertEquals("剂量需大于 0", HealthFormRules.dose(""));
        assertEquals("剂量需大于 0", HealthFormRules.dose("0"));
        assertEquals("最多 3 位小数", HealthFormRules.dose("1.2345"));
        assertNull(HealthFormRules.dose("1"));
        assertNull(HealthFormRules.dose("0.125"));
    }

    @Test
    public void quantityCannotExceedStock() {
        assertEquals("接种数量需大于 0", HealthFormRules.vaccinateQuantity("0", 200, "头"));
        assertEquals("不能超过存栏 200 头", TestRules.plain(HealthFormRules.vaccinateQuantity("201", 200, "头")));
        assertNull(HealthFormRules.vaccinateQuantity("200", 200, "头"));
    }

    @Test
    public void vaccinateDateWithinRange() {
        assertNull(HealthFormRules.vaccinateDate("2026-10-19", "2026-10-01", "2026-10-19"));
        assertEquals("接种日期不能晚于今天", HealthFormRules.vaccinateDate("2026-10-20", "2026-10-01", "2026-10-19"));
        // 补录漏免：不早于计划日期 10月12日
        assertEquals("接种日期不能早于 10月12日", HealthFormRules.vaccinateDate("2026-10-11", "2026-10-12", "2026-10-19"));
    }

    @Test
    public void planAgeAndPostpone() {
        assertEquals("不能早于批次当前日龄 5 日龄", HealthFormRules.planAgeDay("4", 5));
        assertNull(HealthFormRules.planAgeDay("7", 5));
        assertEquals("新日期不能早于今天", HealthFormRules.postponeDate("2026-10-18", "2026-10-19"));
        assertEquals("新日期与原计划日期相同", HealthFormRules.postponeDate("2026-10-19", "2026-10-19"));
        assertNull(HealthFormRules.postponeDate("2026-10-22", "2026-10-19"));
        assertEquals("请填写延期原因", HealthFormRules.postponeReason("  "));
        // 届时日龄：入栏 10月1日 78 日龄 → 10月22日 99 日龄
        assertEquals("届时 99 日龄", ImmuneRules.ageText(78, "2026-10-01", "2026-10-22"));
    }

    @Test
    public void missedAfterSevenDays() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        assertTrue(ImmuneRules.isMissed(LocalDate.of(2026, 10, 12), today));
        assertFalse(ImmuneRules.isMissed(LocalDate.of(2026, 10, 13), today));
        assertEquals(LocalDate.of(2026, 10, 19), ImmuneRules.missedFrom(LocalDate.of(2026, 10, 12)));
    }

    @Test
    public void concentrationAndUsage() {
        assertEquals(HealthFormRules.MSG_CONCENTRATION, HealthFormRules.concentration("500"));
        assertNull(HealthFormRules.concentration("1:500"));
        assertTrue(ConcentrationUtil.differs("1:200", "1:300"));
        assertFalse(ConcentrationUtil.differs("1：300", "1:300"));
        assertEquals("不能超过批号 2025033 结存 2 升",
                TestRules.plain(HealthFormRules.usageQty("2.5", new BigDecimal("2.0"), "2025033", "升")));
        assertNull(HealthFormRules.usageQty("0.5", new BigDecimal("2.0"), "2025033", "升"));
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 10, 0);
        assertEquals("实际时间不能晚于现在", HealthFormRules.actualTime("2026-10-19 11:00:00", null, now));
        assertNull(HealthFormRules.actualTime("2026-10-18 16:20:00", "2026-10-18", now));
    }
}
