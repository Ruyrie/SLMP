package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.AdminBaseTableInfo;
import com.zhimu.server.admin.base.api.DictQueryService;
import com.zhimu.server.admin.base.api.StdQueryService;
import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.FeedStdStage;
import com.zhimu.server.admin.base.entity.FeedStdVersion;
import com.zhimu.server.admin.base.entity.StdWeightPoint;
import com.zhimu.server.admin.base.entity.StdWeightVersion;
import com.zhimu.server.admin.base.mapper.BreedMapper;
import com.zhimu.server.admin.base.mapper.FeedStdStageMapper;
import com.zhimu.server.admin.base.mapper.FeedStdVersionMapper;
import com.zhimu.server.admin.base.mapper.StdWeightPointMapper;
import com.zhimu.server.admin.base.mapper.StdWeightVersionMapper;
import com.zhimu.server.admin.base.vo.BreedStandardsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** API-U12：96 日龄 68.6、82 日龄 53.2、上下限 61.7–75.5、称重间隔 14 天、阶段 育肥前期/育肥猪。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StdQueryServiceImplTest {

    @Mock private BreedMapper breedMapper;
    @Mock private StdWeightVersionMapper weightVersionMapper;
    @Mock private StdWeightPointMapper pointMapper;
    @Mock private FeedStdVersionMapper feedVersionMapper;
    @Mock private FeedStdStageMapper stageMapper;
    @Mock private DictQueryService dictQueryService;

    private StdQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        AdminBaseTableInfo.ensure();
        service = new StdQueryServiceImpl(breedMapper, weightVersionMapper, pointMapper, feedVersionMapper,
                stageMapper, dictQueryService);
        Breed breed = new Breed();
        breed.setId(1101L);
        breed.setCategoryId(101L);
        breed.setName("杜长大");
        breed.setStatus("ENABLED");
        when(breedMapper.selectById(1101L)).thenReturn(breed);
        when(weightVersionMapper.selectList(any())).thenReturn(List.of(version()));
        when(pointMapper.selectList(any())).thenReturn(List.of(
                point(30, "8.00"), point(80, "51.00"), point(90, "62.00"), point(100, "73.00"),
                point(170, "134.50")));
        when(feedVersionMapper.selectList(any())).thenReturn(List.of(feedVersion()));
        when(stageMapper.selectList(any())).thenReturn(List.of(
                stage(1, 28, 60, "保育期", "保育猪", "1.2", 3),
                stage(2, 61, 100, "育肥前期", "育肥猪", "2.5", 3),
                stage(3, 101, 140, "育肥中期", "育肥猪", "2.8", 3),
                stage(4, 141, null, "育肥后期", "育肥猪", "3.1", 2)));
        lenient().when(dictQueryService.nameOf("FEED_TYPE", "COMPLETE")).thenReturn("全价料");
        lenient().when(weightVersionMapper.selectById(101L)).thenReturn(version());
        lenient().when(weightVersionMapper.updateById(any(StdWeightVersion.class))).thenReturn(1);
    }

    @Test
    void u12Returns96AndIntervalsAndStage() {
        BreedStandardsVO vo = service.standards(1101L, 96);
        assertThat(vo.getBreedId()).isEqualTo(1101L);
        assertThat(vo.getWeight().getVersionId()).isEqualTo(101L);
        assertThat(vo.getWeight().getVersionNo()).isEqualTo(1);
        assertThat(vo.getWeight().getSuggestedWeighTimes()).isEqualTo(2);
        assertThat(vo.getWeight().getWeighIntervalDays()).isEqualTo(14);
        assertThat(vo.getWeight().getPoints()).hasSize(5);
        assertThat(vo.getWeight().getPoints().get(2).getLower()).isEqualByComparingTo("55.8");
        assertThat(vo.getWeight().getPoints().get(2).getUpper()).isEqualByComparingTo("68.2");
        assertThat(vo.getAtDay().getDayAge()).isEqualTo(96);
        assertThat(vo.getAtDay().getStdWeight()).isEqualByComparingTo("68.6");
        assertThat(vo.getAtDay().getLower()).isEqualByComparingTo("61.7");
        assertThat(vo.getAtDay().getUpper()).isEqualByComparingTo("75.5");
        assertThat(vo.getAtDay().getStageName()).isEqualTo("育肥前期");
        assertThat(vo.getAtDay().getStageShortName()).isEqualTo("育肥猪");
        assertThat(vo.getFeed().getStages()).hasSize(4);
        assertThat(vo.getFeed().getStages().get(1).getFeedTypeText()).isEqualTo("全价料");
    }

    @Test
    void u12Returns82() {
        BreedStandardsVO vo = service.standards(1101L, 82);
        assertThat(vo.getAtDay().getStdWeight()).isEqualByComparingTo("53.2");
    }

    @Test
    void u12WithoutStandardsOnlyReturnsBreedId() {
        when(weightVersionMapper.selectList(any())).thenReturn(List.of());
        when(feedVersionMapper.selectList(any())).thenReturn(List.of());
        BreedStandardsVO vo = service.standards(1101L, 96);
        assertThat(vo.getWeight()).isNull();
        assertThat(vo.getFeed()).isNull();
        assertThat(vo.getAtDay().getDayAge()).isEqualTo(96);
        assertThat(vo.getAtDay().getStdWeight()).isNull();
    }

    @Test
    void resolveStageAndCurrentVersions() {
        StdQueryService.StageInfo stage = service.resolveStage(1101L, 96);
        assertThat(stage.stageName()).isEqualTo("育肥前期");
        assertThat(stage.stageShortName()).isEqualTo("育肥猪");
        assertThat(stage.mealTimes()).containsExactly("07:00", "11:30", "17:00");
        assertThat(stage.dailyFeed()).isEqualByComparingTo("2.5");
        assertThat(service.resolveStage(1101L, 20)).isNull();
        assertThat(service.currentWeightVersionId(1101L)).isEqualTo(101L);
        assertThat(service.currentFeedVersionId(1101L)).isEqualTo(201L);
    }

    @Test
    void markRecalculatedWritesTimestamp() {
        assertThat(service.markRecalculated(101L)).isTrue();
        assertThat(service.markRecalculated(999L)).isFalse();
    }

    private static StdWeightVersion version() {
        StdWeightVersion version = new StdWeightVersion();
        version.setId(101L);
        version.setBreedId(1101L);
        version.setVersionNo(1);
        version.setStatus("PUBLISHED");
        version.setToleranceRate(new BigDecimal("0.1000"));
        version.setSuggestedWeighTimes(2);
        version.setEffectiveMode("IMMEDIATE");
        version.setVersion(1);
        return version;
    }

    private static StdWeightPoint point(int dayAge, String weight) {
        StdWeightPoint point = new StdWeightPoint();
        point.setVersionId(101L);
        point.setDayAge(dayAge);
        point.setStdWeight(new BigDecimal(weight));
        return point;
    }

    private static FeedStdVersion feedVersion() {
        FeedStdVersion version = new FeedStdVersion();
        version.setId(201L);
        version.setBreedId(1101L);
        version.setVersionNo(1);
        version.setStatus("PUBLISHED");
        version.setDeviationRate(new BigDecimal("0.1000"));
        version.setVersion(1);
        return version;
    }

    private static FeedStdStage stage(int sort, int start, Integer end, String name, String shortName,
                                      String daily, int meals) {
        FeedStdStage stage = new FeedStdStage();
        stage.setId(2000L + sort);
        stage.setVersionId(201L);
        stage.setSortNo(sort);
        stage.setDayAgeStart(start);
        stage.setDayAgeEnd(end);
        stage.setStageName(name);
        stage.setStageShortName(shortName);
        stage.setFeedTypeCode("COMPLETE");
        stage.setSuggestedFeedName("育肥全价料");
        stage.setDailyFeed(new BigDecimal(daily));
        stage.setMealsPerDay(meals);
        stage.setMealTimes(meals == 2 ? "07:00,17:00" : "07:00,11:30,17:00");
        return stage;
    }
}
