package com.zhimu.slmp.feed.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zhimu.slmp.feed.model.vo.StdPointVO;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** 称重偏离计算（技术文档 2.9）：插值、平均、偏离与生长预判、日增重、建议抽样数。 */
public class GrowthCalcTest {

    private static final List<StdPointVO> STD = Arrays.asList(
            new StdPointVO(70, new BigDecimal("41.00")), new StdPointVO(80, new BigDecimal("51.00")),
            new StdPointVO(90, new BigDecimal("62.00")), new StdPointVO(100, new BigDecimal("73.00")));
    private static final BigDecimal ALLOWED = new BigDecimal("0.1000");

    @Test
    public void interpolate_96DaysIs68point6_82DaysIs53point2() {
        assertEquals("68.6", GrowthCalc.interpolate(STD, 96).toPlainString());
        assertEquals("53.2", GrowthCalc.interpolate(STD, 82).toPlainString());
        assertEquals("62.0", GrowthCalc.interpolate(STD, 90).toPlainString());
        // 超出首末点为无标准
        assertNull(GrowthCalc.interpolate(STD, 69));
        assertNull(GrowthCalc.interpolate(STD, 101));
    }

    @Test
    public void deviation_60Against68point6_isMinus12point5Percent_slow() {
        BigDecimal avg = GrowthCalc.average(new BigDecimal("1200"), 20);
        assertEquals(0, new BigDecimal("60").compareTo(avg));
        BigDecimal dev = GrowthCalc.deviation(avg, new BigDecimal("68.6"));
        assertEquals(0, new BigDecimal("-0.1254").compareTo(dev));
        assertEquals("−12.5%", FeedFormat.signedPercent(dev));
        assertTrue(GrowthCalc.outOfRange(dev, ALLOWED));
        assertEquals(GrowthCalc.SLOW, GrowthCalc.growthStatus(avg, new BigDecimal("68.6"), ALLOWED));
        assertEquals("提交后批次标为生长偏慢，下次称重回到范围内自动取消",
                GrowthCalc.predictNote(GrowthCalc.SLOW, GrowthCalc.NORMAL));
    }

    @Test
    public void growthStatus_fastNormalAndNoStd() {
        BigDecimal std = new BigDecimal("68.6");
        assertEquals(GrowthCalc.FAST, GrowthCalc.growthStatus(new BigDecimal("76.3"), std, ALLOWED));
        assertEquals(GrowthCalc.NORMAL, GrowthCalc.growthStatus(new BigDecimal("62.0"), std, ALLOWED));
        // 回到范围内而批次原来偏慢：说明会取消标记
        assertEquals("提交后取消生长偏慢标记", GrowthCalc.predictNote(GrowthCalc.NORMAL, GrowthCalc.SLOW));
        assertEquals(GrowthCalc.NO_STD, GrowthCalc.growthStatus(new BigDecimal("60"), null, ALLOWED));
        assertFalse(GrowthCalc.outOfRange(new BigDecimal("-0.1000"), ALLOWED));
    }

    @Test
    public void dailyGain_againstLastWeighAndIntake() {
        assertEquals("0.81", GrowthCalc.dailyGain(new BigDecimal("60.00"), new BigDecimal("48.60"), "2026-10-19",
                "2026-10-05").toPlainString());
        assertEquals("0.90", GrowthCalc.dailyGain(new BigDecimal("48.60"), new BigDecimal("45.00"), "2026-10-05",
                "2026-10-01").toPlainString());
        assertNull(GrowthCalc.dailyGain(new BigDecimal("60"), null, "2026-10-19", "2026-10-05"));
        assertNull(GrowthCalc.dailyGain(new BigDecimal("60"), new BigDecimal("48.6"), "2026-10-05", "2026-10-05"));
    }

    @Test
    public void estimateDayAgeAndSuggestedSample() {
        assertEquals(Integer.valueOf(96), GrowthCalc.estimateDayAge(78, "2026-10-01", "2026-10-19"));
        assertEquals(Integer.valueOf(30), GrowthCalc.suggestedSample(200, new BigDecimal("0.1500")));
        assertEquals(Integer.valueOf(23), GrowthCalc.suggestedSample(150, new BigDecimal("0.1500")));
    }
}
