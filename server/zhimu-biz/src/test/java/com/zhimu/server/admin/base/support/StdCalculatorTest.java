package com.zhimu.server.admin.base.support;

import com.zhimu.server.admin.base.entity.FeedStdStage;
import com.zhimu.server.admin.base.entity.StdWeightPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 标准体重折算口径（管理端-03 2.7；固定示例：96 日龄 68.6、82 日龄 53.2、上下限 61.7–75.5）。 */
class StdCalculatorTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.1000");

    @Test
    void interpolates96And82AndBounds() {
        List<StdWeightPoint> points = List.of(point(80, "51.00"), point(90, "62.00"), point(100, "73.00"));
        StdCalculator.StandardAt at96 = StdCalculator.interpolate(points, 96, TOLERANCE);
        assertThat(at96.stdWeight()).isEqualByComparingTo("68.6");
        assertThat(at96.lower()).isEqualByComparingTo("61.7");
        assertThat(at96.upper()).isEqualByComparingTo("75.5");

        StdCalculator.StandardAt at82 = StdCalculator.interpolate(points, 82, TOLERANCE);
        assertThat(at82.stdWeight()).isEqualByComparingTo("53.2");
    }

    @Test
    void returnsExactPointAndOutOfRangeNull() {
        List<StdWeightPoint> points = List.of(point(90, "62.00"), point(100, "73.00"));
        assertThat(StdCalculator.interpolate(points, 90, TOLERANCE).stdWeight()).isEqualByComparingTo("62.0");
        assertThat(StdCalculator.interpolate(points, 89, TOLERANCE)).isNull();
        assertThat(StdCalculator.interpolate(points, 101, TOLERANCE)).isNull();
    }

    @Test
    void dailyGainTwoDecimalsAndWeighInterval() {
        List<StdWeightPoint> points = StdCalculator.sorted(List.of(point(100, "73.00"), point(90, "62.00")));
        assertThat(StdCalculator.dailyGain(points, 0)).isNull();
        assertThat(StdCalculator.dailyGain(points, 1)).isEqualByComparingTo("1.10");
        assertThat(StdCalculator.weighInterval(2)).isEqualTo(14);
        assertThat(StdCalculator.weighInterval(3)).isEqualTo(9);
        assertThat(StdCalculator.weighInterval(null)).isEqualTo(14);
    }

    @Test
    void resolvesStageAndLastStageOpenEnded() {
        List<FeedStdStage> stages = List.of(stage(1, 28, 60, "保育期", "保育猪"),
                stage(2, 61, 100, "育肥前期", "育肥猪"),
                stage(3, 141, null, "育肥后期", "育肥猪"));
        assertThat(StdCalculator.resolveStage(stages, 96).getStageShortName()).isEqualTo("育肥猪");
        assertThat(StdCalculator.resolveStage(stages, 60).getStageName()).isEqualTo("保育期");
        assertThat(StdCalculator.resolveStage(stages, 500).getStageName()).isEqualTo("育肥后期");
        assertThat(StdCalculator.resolveStage(stages, 20)).isNull();
    }

    private static StdWeightPoint point(int dayAge, String weight) {
        StdWeightPoint point = new StdWeightPoint();
        point.setDayAge(dayAge);
        point.setStdWeight(new BigDecimal(weight));
        return point;
    }

    private static FeedStdStage stage(int sort, int start, Integer end, String name, String shortName) {
        FeedStdStage stage = new FeedStdStage();
        stage.setSortNo(sort);
        stage.setDayAgeStart(start);
        stage.setDayAgeEnd(end);
        stage.setStageName(name);
        stage.setStageShortName(shortName);
        return stage;
    }
}
