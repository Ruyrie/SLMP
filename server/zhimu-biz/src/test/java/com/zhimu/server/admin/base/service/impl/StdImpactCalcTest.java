package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.entity.FeedStdStage;
import com.zhimu.server.admin.base.entity.StdWeightPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 发布影响预估里“最早改动日龄”的计算。 */
class StdImpactCalcTest {

    private static StdWeightPoint point(int day, String weight) {
        StdWeightPoint point = new StdWeightPoint();
        point.setDayAge(day);
        point.setStdWeight(new BigDecimal(weight));
        return point;
    }

    private static FeedStdStage stage(int sort, int start, String daily) {
        FeedStdStage stage = new FeedStdStage();
        stage.setSortNo(sort);
        stage.setStageName("阶段" + sort);
        stage.setDayAgeStart(start);
        stage.setFeedTypeCode("FATTEN");
        stage.setDailyFeed(new BigDecimal(daily));
        stage.setMealsPerDay(3);
        return stage;
    }

    @Test
    void 体重标准_取最早变化或删除的日龄_没有生效版本取第一个点() {
        List<StdWeightPoint> online = List.of(point(30, "10.0"), point(60, "25.0"), point(90, "45.0"));
        assertThat(StdAdminServiceImpl.firstChangedWeightDayAge(
                List.of(point(30, "10.00"), point(60, "26.0"), point(90, "45.0")), online)).isEqualTo(60);
        assertThat(StdAdminServiceImpl.firstChangedWeightDayAge(
                List.of(point(30, "10.0"), point(90, "45.0")), online)).isEqualTo(60);
        assertThat(StdAdminServiceImpl.firstChangedWeightDayAge(online, online)).isNull();
        assertThat(StdAdminServiceImpl.firstChangedWeightDayAge(online, null)).isEqualTo(30);
    }

    @Test
    void 饲喂标准_取最早改动阶段的起始日龄() {
        List<FeedStdStage> online = List.of(stage(1, 1, "0.5"), stage(2, 31, "1.2"), stage(3, 61, "2.0"));
        assertThat(StdAdminServiceImpl.firstChangedStageDayAge(
                List.of(stage(1, 1, "0.5"), stage(2, 31, "1.2"), stage(3, 61, "2.2")), online)).isEqualTo(61);
        assertThat(StdAdminServiceImpl.firstChangedStageDayAge(online, online)).isNull();
        assertThat(StdAdminServiceImpl.firstChangedStageDayAge(online, null)).isEqualTo(1);
    }
}
