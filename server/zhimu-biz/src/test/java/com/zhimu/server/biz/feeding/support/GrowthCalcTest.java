package com.zhimu.server.biz.feeding.support;

import com.zhimu.server.biz.feeding.vo.WeighVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 标准体重插值、生长结果、日增重、抽样与称重间隔。 */
class GrowthCalcTest {

    private static final List<WeighVO.StdPoint> POINTS = List.of(
            point(80, 51.0), point(90, 62.0), point(100, 73.0));

    @Test
    void interpolatesBetweenPointsAndDoesNotExtrapolate() {
        assertThat(GrowthCalc.interpolate(POINTS, 96)).isEqualByComparingTo("68.6");
        assertThat(GrowthCalc.interpolate(POINTS, 90)).isEqualByComparingTo("62.0");
        assertThat(GrowthCalc.interpolate(POINTS, 79)).isNull();
        assertThat(GrowthCalc.interpolate(POINTS, 101)).isNull();
        assertThat(GrowthCalc.interpolate(List.of(), 90)).isNull();
    }

    @Test
    void poultryInterpolationKeepsSubKilogramPrecision() {
        List<WeighVO.StdPoint> points = List.of(point(1, 0.04), point(7, 0.16));
        assertThat(GrowthCalc.interpolate(points, 1)).isEqualByComparingTo("0.04");
        assertThat(GrowthCalc.interpolate(points, 2)).isEqualByComparingTo("0.06");
    }

    @Test
    void growthResultUsesToleranceStrictly() {
        assertThat(GrowthCalc.growthResult(new BigDecimal("-0.1254"), new BigDecimal("0.1000"))).isEqualTo("SLOW");
        assertThat(GrowthCalc.growthResult(new BigDecimal("0.1254"), new BigDecimal("0.1000"))).isEqualTo("FAST");
        assertThat(GrowthCalc.growthResult(new BigDecimal("-0.1000"), new BigDecimal("0.1000"))).isEqualTo("NORMAL");
        assertThat(GrowthCalc.growthResult(new BigDecimal("-0.0100"), new BigDecimal("0.1000"))).isEqualTo("NORMAL");
        assertThat(GrowthCalc.growthResult(null, new BigDecimal("0.1000"))).isEqualTo("NO_STD");
    }

    @Test
    void deviationAndDailyGain() {
        assertThat(GrowthCalc.deviation(new BigDecimal("60.0"), new BigDecimal("68.6")))
                .isEqualByComparingTo("-0.1254");
        assertThat(GrowthCalc.dailyGain(new BigDecimal("60.0"), new BigDecimal("48.6"), 14))
                .isEqualByComparingTo("0.81");
        assertThat(GrowthCalc.dailyGain(new BigDecimal("48.6"), new BigDecimal("45.0"), 4))
                .isEqualByComparingTo("0.90");
    }

    @Test
    void sampleAndInterval() {
        assertThat(GrowthCalc.suggestedSample(200)).isEqualTo(30);
        assertThat(GrowthCalc.suggestedSample(150)).isEqualTo(23);
        assertThat(GrowthCalc.intervalDays(1)).isEqualTo(28);
        assertThat(GrowthCalc.intervalDays(2)).isEqualTo(14);
        assertThat(GrowthCalc.intervalDays(3)).isEqualTo(9);
        assertThat(GrowthCalc.intervalDays(4)).isEqualTo(7);
    }

    private static WeighVO.StdPoint point(int dayAge, double weight) {
        WeighVO.StdPoint point = new WeighVO.StdPoint();
        point.setDayAge(dayAge);
        point.setStdWeight(BigDecimal.valueOf(weight));
        return point;
    }
}
