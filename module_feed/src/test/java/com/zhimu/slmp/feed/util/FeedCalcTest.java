package com.zhimu.slmp.feed.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zhimu.slmp.feed.TestRules;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 投喂偏差与异常（技术文档 2.9）、实投校验、超结存判断、方案校验与餐次命名。 */
public class FeedCalcTest {

    private static final BigDecimal PLAN = new BigDecimal("160");
    private static final BigDecimal THRESHOLD = new BigDecimal("0.1000");

    @Test
    public void deviation_140Against160_isMinus12point5Percent() {
        BigDecimal dev = FeedCalc.deviation(PLAN, new BigDecimal("140"));
        assertEquals(0, new BigDecimal("-0.1250").compareTo(dev));
        assertEquals(FeedCalc.UNDER, FeedCalc.abnormalType(dev, THRESHOLD));
        assertEquals("实际比计划少 12.5%，超过 10%。确认后这次投喂会标为投喂异常。",
                FeedCalc.abnormalText(dev, THRESHOLD));
        assertEquals("200 头 × 每头日投喂 2.4 千克 ÷ 3 餐 · 偏差 -12.5%",
                TestRules.plain(FeedCalc.planSource(200, new BigDecimal("2.400"), 3, dev)));
    }

    @Test
    public void deviationEqualToThreshold_isNotAbnormal() {
        BigDecimal under = FeedCalc.deviation(PLAN, new BigDecimal("144"));
        BigDecimal over = FeedCalc.deviation(PLAN, new BigDecimal("176"));
        assertEquals(FeedCalc.NONE, FeedCalc.abnormalType(under, THRESHOLD));
        assertEquals(FeedCalc.NONE, FeedCalc.abnormalType(over, THRESHOLD));
        assertNull(FeedCalc.abnormalText(over, THRESHOLD));
        BigDecimal more = FeedCalc.deviation(PLAN, new BigDecimal("184"));
        assertEquals(FeedCalc.OVER, FeedCalc.abnormalType(more, THRESHOLD));
        assertEquals("实际比计划多 15.0%，超过 10%。确认后这次投喂会标为投喂异常。", FeedCalc.abnormalText(more, THRESHOLD));
    }

    @Test
    public void checkActual_rules() {
        assertEquals(FeedCalc.ERR_EMPTY, FeedCalc.checkActual(""));
        assertEquals(FeedCalc.ERR_NOT_POSITIVE, FeedCalc.checkActual("0"));
        assertEquals(FeedCalc.ERR_SCALE, FeedCalc.checkActual("140.25"));
        assertEquals(FeedCalc.ERR_TOO_LARGE, FeedCalc.checkActual("100000"));
        assertNull(FeedCalc.checkActual("140.5"));
    }

    @Test
    public void stock_newRecordUsesActual_editSameFeedUsesDifference() {
        BigDecimal stock = new BigDecimal("500");
        assertTrue(FeedCalc.exceedsStock(new BigDecimal("560"), stock, null));
        assertFalse(FeedCalc.exceedsStock(new BigDecimal("500"), stock, null));
        // 修改同一饲料：640 − 原 158 = 482，不超过结存 500
        assertFalse(FeedCalc.exceedsStock(new BigDecimal("640"), stock, new BigDecimal("158")));
        assertTrue(FeedCalc.exceedsStock(new BigDecimal("660"), stock, new BigDecimal("158")));
    }

    @Test
    public void planValidator_continuityAndDuplicateTimes() {
        com.zhimu.slmp.feed.model.request.FeedPlanStageRequest a = stage("育肥前期", 61, 100, "2.4",
                Arrays.asList("07:00", "11:30", "17:00"));
        com.zhimu.slmp.feed.model.request.FeedPlanStageRequest b = stage("育肥中期", 102, 140, "0",
                Arrays.asList("07:00", "07:00", "17:00"));
        Map<String, String> e = FeedPlanValidator.validate(Arrays.asList(a, b), 96);
        assertEquals("阶段日龄要连续，应从 101 日龄开始", e.get("stages[1].day_age_start"));
        assertEquals(FeedPlanValidator.ERR_QTY_POSITIVE, e.get("stages[1].daily_qty_per_head"));
        assertEquals(FeedPlanValidator.ERR_TIME_DUP, e.get("stages[1].meal_times"));
        assertEquals("只有最后一个阶段可以不填结束日龄", FeedPlanValidator.validate(Arrays.asList(
                stage("育肥前期", 61, null, "2.4", Arrays.asList("07:00", "11:30", "17:00")),
                stage("育肥中期", 101, null, "2.8", Arrays.asList("07:00", "11:30", "17:00"))), 96)
                .get("stages[0].day_age_end"));
        assertEquals(1, FeedPlanValidator.stageIndexOf("stages[1].daily_qty_per_head"));
        assertEquals(FeedPlanValidator.ERR_THRESHOLD, FeedPlanValidator.checkThreshold(new BigDecimal("0.005")));
        assertNull(FeedPlanValidator.checkThreshold(new BigDecimal("0.10")));
    }

    @Test
    public void mealNames_boundariesAt10And15() {
        assertEquals("BREAKFAST", MealNameResolver.codeOf("09:59"));
        assertEquals("LUNCH", MealNameResolver.codeOf("10:00"));
        assertEquals("LUNCH", MealNameResolver.codeOf("14:59"));
        assertEquals("DINNER", MealNameResolver.codeOf("15:00"));
        List<String> names = MealNameResolver.names(Arrays.asList("07:00", "09:00", "17:00"));
        assertEquals(Arrays.asList("早餐", "加餐", "晚餐"), names);
    }

    private static com.zhimu.slmp.feed.model.request.FeedPlanStageRequest stage(String name, int start, Integer end,
                                                                                String qty, List<String> times) {
        com.zhimu.slmp.feed.model.request.FeedPlanStageRequest r = new com.zhimu.slmp.feed.model.request.FeedPlanStageRequest();
        r.stageName = name;
        r.dayAgeStart = start;
        r.dayAgeEnd = end;
        r.feedId = 4001;
        r.dailyQtyPerHead = new BigDecimal(qty);
        r.mealsPerDay = times.size();
        r.mealTimes = times;
        return r;
    }
}
