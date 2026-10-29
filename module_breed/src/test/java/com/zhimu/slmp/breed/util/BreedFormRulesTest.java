package com.zhimu.slmp.breed.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.breed.TestRules;
import com.zhimu.slmp.breed.model.BreedCodes;

import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Map;

/**
 * 配种 / 妊检、产仔、断奶、孵化三步的表单校验与日期推算（技术文档 2.9“BreedFormRules 各数量与日期规则”）。
 */
public class BreedFormRulesTest {

    @Rule
    public TestRules rules = new TestRules();

    // ===== 配种 =====

    @Test
    public void mating_requiresAnimal() {
        Map<String, String> e = BreedFormRules.mating(null, "2026-10-19", null, false, null,
                BreedCodes.METHOD_ARTIFICIAL, null);
        assertEquals("请选择母猪", e.get(BreedFormRules.F_ANIMAL));
    }

    @Test
    public void mating_dateNotAfterTodayAndNotBeforeWean() {
        Map<String, String> future = BreedFormRules.mating(4161L, "2026-10-20", "2026-10-12", true, null,
                BreedCodes.METHOD_ARTIFICIAL, null);
        assertEquals("配种日期不能晚于今天", future.get(BreedFormRules.F_MATING_DATE));

        Map<String, String> early = BreedFormRules.mating(4161L, "2026-10-11", "2026-10-12", true, null,
                BreedCodes.METHOD_ARTIFICIAL, null);
        assertEquals("配种日期不能早于上次断奶日期 10月12日", early.get(BreedFormRules.F_MATING_DATE));

        assertTrue(BreedFormRules.mating(4161L, "2026-10-12", "2026-10-12", true, null,
                BreedCodes.METHOD_NATURAL, "").isEmpty());
    }

    @Test
    public void mating_reMatingNotBeforeFirstMating_boarMax20() {
        Map<String, String> e = BreedFormRules.mating(4155L, "2026-09-20", "2026-09-24", false, "2026-09-24",
                BreedCodes.METHOD_ARTIFICIAL, "123456789012345678901");
        assertEquals("配种日期不能早于首次配种日期 9月24日", e.get(BreedFormRules.F_MATING_DATE));
        assertEquals("最多 20 个字", e.get(BreedFormRules.F_BOAR));
    }

    @Test
    public void expectedDue_usesFirstMatingWhenReMating() {
        // 10月19日 + 114 = 2027年2月10日 周三；同一情期再配按首次配种 9月24日 → 2027年1月16日 周六
        LocalDate fresh = BreedFormRules.expectedDueDate(null, "2026-10-19", 114);
        assertEquals(LocalDate.of(2027, 2, 10), fresh);
        assertEquals("2027年2月10日 周三", BreedTextFormatter.fullDate(fresh));
        LocalDate again = BreedFormRules.expectedDueDate("2026-09-24", "2026-10-19", 114);
        assertEquals("2027年1月16日 周六", BreedTextFormatter.fullDate(again));
    }

    // ===== 妊检 =====

    @Test
    public void pregCheck_requiresResultAndDateAfterMating() {
        Map<String, String> e = BreedFormRules.pregCheck("2026-09-20", "2026-09-24", null);
        assertEquals("妊检日期不能早于配种日期 9月24日", e.get(BreedFormRules.F_CHECK_DATE));
        assertEquals("请选择妊检结果", e.get(BreedFormRules.F_RESULT));
        assertTrue(BreedFormRules.pregCheck("2026-10-19", "2026-09-24", BreedCodes.POSITIVE).isEmpty());
    }

    @Test
    public void pregCheck_positiveNeedsEighteenDaysAfterMating() {
        // 配种当天、第 17 天登记阳性被拦；满 18 天可以；阴性不限
        assertEquals(BreedFormRules.MSG_POSITIVE_TOO_EARLY,
                BreedFormRules.pregCheck("2026-10-01", "2026-10-01", BreedCodes.POSITIVE).get(BreedFormRules.F_RESULT));
        assertEquals(BreedFormRules.MSG_POSITIVE_TOO_EARLY,
                BreedFormRules.pregCheck("2026-10-18", "2026-10-01", BreedCodes.POSITIVE).get(BreedFormRules.F_RESULT));
        assertTrue(BreedFormRules.pregCheck("2026-10-19", "2026-10-01", BreedCodes.POSITIVE).isEmpty());
        assertTrue(BreedFormRules.pregCheck("2026-10-01", "2026-10-01", BreedCodes.NEGATIVE).isEmpty());
    }

    @Test
    public void dueLine3_showsStaffNamesBeforeReminder() {
        assertEquals("负责 李明、张伟 · 已提醒 10月16日、今天", BreedTextFormatter.dueLine3(
                java.util.Arrays.asList("李明", "张伟"), "已提醒 10月16日、今天"));
        assertEquals("负责 李明", BreedTextFormatter.dueLine3(java.util.Collections.singletonList("李明"), null));
        assertEquals("10月25日提醒", BreedTextFormatter.dueLine3(java.util.Collections.emptyList(), "10月25日提醒"));
        assertNull(BreedTextFormatter.dueLine3(null, null));
    }

    // ===== 产仔 =====

    @Test
    public void farrowing_sumMismatch_marksBothFields() {
        String msg = "活仔 11 头 + 死胎 1 头 = 12 头，与总产仔 13 头不一致";
        assertEquals(msg, BreedFormRules.sumError("13", "11", "1"));
        Map<String, String> e = BreedFormRules.farrowing(4131L, "2026-10-19", "2026-06-27", "13", "11", "1",
                true, false, false);
        assertEquals(msg, e.get(BreedFormRules.F_LIVE));
        assertEquals(msg, e.get(BreedFormRules.F_STILL));
        assertNull(BreedFormRules.sumError("13", "12", "1"));
    }

    @Test
    public void farrowing_countRules() {
        assertEquals("请填写总产仔数", BreedFormRules.farrowing(4131L, "2026-10-19", null, "", "0", "0",
                false, false, false).get(BreedFormRules.F_TOTAL));
        assertEquals("总产仔数至少为 1 头", BreedFormRules.farrowing(4131L, "2026-10-19", null, "0", "0", "0",
                false, false, false).get(BreedFormRules.F_TOTAL));
        assertEquals("活仔数不能多于总产仔 13 头", BreedFormRules.farrowing(4131L, "2026-10-19", null, "13", "14",
                "0", false, false, false).get(BreedFormRules.F_LIVE));
        assertEquals("产仔日期不能早于配种日期 6月27日", BreedFormRules.farrowing(4131L, "2026-06-20",
                "2026-06-27", "12", "11", "1", false, false, false).get(BreedFormRules.F_FARROW_DATE));
    }

    @Test
    public void farrowing_transferNeedsLiveAndCapacityConfirm() {
        Map<String, String> zero = BreedFormRules.farrowing(4131L, "2026-10-19", null, "2", "0", "2", true,
                false, false);
        assertEquals("活仔数为 0，不能转为新批次。请关闭“活仔一键转为新批次”。", zero.get(BreedFormRules.F_TRANSFER));
        Map<String, String> over = BreedFormRules.farrowing(4131L, "2026-10-19", null, "50", "45", "5", true,
                true, false);
        assertEquals("请先确认超出容量", over.get(BreedFormRules.F_CAPACITY));
        assertTrue(BreedFormRules.farrowing(4131L, "2026-10-19", null, "50", "45", "5", true, true, true)
                .isEmpty());
    }

    // ===== 断奶 =====

    @Test
    public void weaning_limitsAndWeight() {
        Map<String, String> e = BreedFormRules.weaning(4109L, "2026-09-20", "2026-09-21", "13", 12, "85.25");
        assertEquals("断奶日期不能早于产仔日期 9月21日", e.get(BreedFormRules.F_WEAN_DATE));
        assertEquals("不能多于产仔时的活仔 12 头", e.get(BreedFormRules.F_WEANED));
        assertEquals("请输入大于 0 的重量，最多 1 位小数", e.get(BreedFormRules.F_WEIGHT));
        assertTrue(BreedFormRules.weaning(4109L, "2026-10-19", "2026-09-21", "11", 12, "85.5").isEmpty());
    }

    // ===== 孵化三步 =====

    @Test
    public void incubation_setCandleHatchRules() {
        Map<String, String> set = BreedFormRules.incubationSet(null, "2026-10-19", "");
        assertEquals("请选择种禽群", set.get(BreedFormRules.F_FLOCK));
        assertEquals("请填写入孵蛋数", set.get(BreedFormRules.F_SET_COUNT));

        Map<String, String> candle = BreedFormRules.candling("2026-09-27", "2026-09-28", "9601", 9600);
        assertEquals("照蛋日期不能早于入孵日期", candle.get(BreedFormRules.F_CANDLE_DATE));
        assertEquals("不能多于入孵蛋数 9,600 枚", TestRules.plain(candle.get(BreedFormRules.F_FERTILE)));

        Map<String, String> hatch = BreedFormRules.hatch("2026-10-19", "2026-10-05", "9121", "9200", 9120,
                true, null, false, false);
        assertEquals("不能多于受精蛋数 9,120 枚", TestRules.plain(hatch.get(BreedFormRules.F_HATCHED)));
        assertEquals("健雏数不能多于出雏数 9,121 只", TestRules.plain(hatch.get(BreedFormRules.F_HEALTHY)));
        assertEquals("请选择入栏鸡舍", hatch.get(BreedFormRules.F_TARGET_PEN));

        Map<String, String> zero = BreedFormRules.hatch("2026-10-19", "2026-10-05", "10", "0", 9120, true, 2203L,
                false, false);
        assertEquals("健雏数为 0，不能转为新批次", zero.get(BreedFormRules.F_TRANSFER));
        assertFalse(BreedFormRules.hatch("2026-10-19", "2026-10-05", "8650", "8480", 9120, true, 2203L, false,
                false).containsKey(BreedFormRules.F_HATCHED));
    }

    @Test
    public void incubation_hatchDateAndRates() {
        // 9月28日 + 21 = 10月19日；10月19日 + 21 = 11月9日 周一
        assertEquals(LocalDate.of(2026, 10, 19), BreedFormRules.expectedHatchDate("2026-09-28", 21));
        assertEquals("11月9日 周一", BreedTextFormatter.fullDate(BreedFormRules.expectedHatchDate(
                DateFormatter.todayString(), 21)));
        assertEquals("95.0%（9,120 ÷ 9,600）", BreedTextFormatter.fertilityText(9120, 9600));
        assertEquals("90.1%", BreedTextFormatter.percent(BreedTextFormatter.ratio(8650, 9600)));
        assertEquals("已登记入孵，预计 11月9日 周一出雏", BreedTextFormatter.setDone("2026-11-09"));
    }
}
