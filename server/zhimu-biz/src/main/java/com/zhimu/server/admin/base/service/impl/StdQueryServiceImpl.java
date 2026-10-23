package com.zhimu.server.admin.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.DictQueryService;
import com.zhimu.server.admin.base.api.StdQueryService;
import com.zhimu.server.admin.base.convert.AdminBaseConvert;
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
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.support.StdCalculator;
import com.zhimu.server.admin.base.vo.BreedStandardsVO;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** API-U12 品种标准与生长阶段解析；只读 PUBLISHED 版本。 */
@Service
@RequiredArgsConstructor
public class StdQueryServiceImpl implements StdQueryService {

    private final BreedMapper breedMapper;
    private final StdWeightVersionMapper stdWeightVersionMapper;
    private final StdWeightPointMapper stdWeightPointMapper;
    private final FeedStdVersionMapper feedStdVersionMapper;
    private final FeedStdStageMapper feedStdStageMapper;
    private final DictQueryService dictQueryService;

    @Override
    public BreedStandardsVO standards(Long breedId, Integer dayAge) {
        Breed breed = breedId == null ? null : breedMapper.selectById(breedId);
        if (breed == null) {
            throw BaseErrors.notFound();
        }
        BreedStandardsVO result = new BreedStandardsVO();
        result.setBreedId(breed.getId());

        StdWeightVersion weightVersion = publishedWeight(breedId);
        List<StdWeightPoint> points = List.of();
        if (weightVersion != null) {
            points = StdCalculator.sorted(stdWeightPointMapper.selectList(Wrappers.<StdWeightPoint>lambdaQuery()
                    .eq(StdWeightPoint::getVersionId, weightVersion.getId())));
            BreedStandardsVO.WeightStandardVO weight = new BreedStandardsVO.WeightStandardVO();
            weight.setVersionId(weightVersion.getId());
            weight.setVersionNo(weightVersion.getVersionNo());
            weight.setToleranceRate(weightVersion.getToleranceRate());
            weight.setSuggestedWeighTimes(weightVersion.getSuggestedWeighTimes());
            weight.setWeighIntervalDays(StdCalculator.weighInterval(weightVersion.getSuggestedWeighTimes()));
            List<BreedStandardsVO.WeightPointVO> pointVOs = new ArrayList<>();
            for (StdWeightPoint point : points) {
                BreedStandardsVO.WeightPointVO vo = new BreedStandardsVO.WeightPointVO();
                vo.setDayAge(point.getDayAge());
                vo.setStdWeight(StdCalculator.round1(point.getStdWeight()));
                vo.setLower(StdCalculator.lower(point.getStdWeight(), weightVersion.getToleranceRate()));
                vo.setUpper(StdCalculator.upper(point.getStdWeight(), weightVersion.getToleranceRate()));
                pointVOs.add(vo);
            }
            weight.setPoints(pointVOs);
            result.setWeight(weight);
        }

        FeedStdVersion feedVersion = publishedFeed(breedId);
        List<FeedStdStage> stages = List.of();
        if (feedVersion != null) {
            stages = feedStdStageMapper.selectList(Wrappers.<FeedStdStage>lambdaQuery()
                    .eq(FeedStdStage::getVersionId, feedVersion.getId())
                    .orderByAsc(FeedStdStage::getSortNo));
            BreedStandardsVO.FeedStandardVO feed = new BreedStandardsVO.FeedStandardVO();
            feed.setVersionId(feedVersion.getId());
            feed.setVersionNo(feedVersion.getVersionNo());
            feed.setDeviationRate(feedVersion.getDeviationRate());
            List<BreedStandardsVO.FeedStageVO> stageVOs = new ArrayList<>();
            for (FeedStdStage stage : stages) {
                BreedStandardsVO.FeedStageVO vo = new BreedStandardsVO.FeedStageVO();
                vo.setStageId(stage.getId());
                vo.setSortNo(stage.getSortNo());
                vo.setStageName(stage.getStageName());
                vo.setStageShortName(stage.getStageShortName());
                vo.setDayAgeStart(stage.getDayAgeStart());
                vo.setDayAgeEnd(stage.getDayAgeEnd());
                vo.setFeedTypeCode(stage.getFeedTypeCode());
                vo.setFeedTypeText(dictQueryService.nameOf("FEED_TYPE", stage.getFeedTypeCode()));
                vo.setSuggestedFeedName(stage.getSuggestedFeedName());
                vo.setDailyFeed(stage.getDailyFeed());
                vo.setMealsPerDay(stage.getMealsPerDay());
                vo.setMealTimes(AdminBaseConvert.splitMealTimes(stage.getMealTimes()));
                stageVOs.add(vo);
            }
            feed.setStages(stageVOs);
            result.setFeed(feed);
        }

        if (dayAge != null) {
            BreedStandardsVO.AtDayVO atDay = new BreedStandardsVO.AtDayVO();
            atDay.setDayAge(dayAge);
            if (weightVersion != null) {
                StdCalculator.StandardAt at = StdCalculator.interpolate(points, dayAge, weightVersion.getToleranceRate());
                if (at != null) {
                    atDay.setStdWeight(at.stdWeight());
                    atDay.setLower(at.lower());
                    atDay.setUpper(at.upper());
                }
            }
            FeedStdStage stage = StdCalculator.resolveStage(stages, dayAge);
            if (stage != null) {
                atDay.setStageName(stage.getStageName());
                atDay.setStageShortName(stage.getStageShortName());
            }
            result.setAtDay(atDay);
        }
        return result;
    }

    @Override
    public StageInfo resolveStage(Long breedId, Integer dayAge) {
        if (breedId == null || dayAge == null) {
            return null;
        }
        FeedStdVersion version = publishedFeed(breedId);
        if (version == null) {
            return null;
        }
        List<FeedStdStage> stages = feedStdStageMapper.selectList(Wrappers.<FeedStdStage>lambdaQuery()
                .eq(FeedStdStage::getVersionId, version.getId())
                .orderByAsc(FeedStdStage::getSortNo));
        FeedStdStage stage = StdCalculator.resolveStage(stages, dayAge);
        if (stage == null) {
            return null;
        }
        return new StageInfo(version.getId(), stage.getStageName(), stage.getStageShortName(),
                stage.getDayAgeStart(), stage.getDayAgeEnd(),
                AdminBaseConvert.splitMealTimes(stage.getMealTimes()), stage.getDailyFeed(), stage.getMealsPerDay());
    }

    @Override
    public Long currentWeightVersionId(Long breedId) {
        StdWeightVersion version = publishedWeight(breedId);
        return version == null ? null : version.getId();
    }

    @Override
    public Long currentFeedVersionId(Long breedId) {
        FeedStdVersion version = publishedFeed(breedId);
        return version == null ? null : version.getId();
    }

    @Override
    public boolean markRecalculated(Long weightVersionId) {
        StdWeightVersion version = weightVersionId == null ? null : stdWeightVersionMapper.selectById(weightVersionId);
        if (version == null) {
            return false;
        }
        version.setRecalculatedAt(BeijingTime.now());
        return stdWeightVersionMapper.updateById(version) > 0;
    }

    private StdWeightVersion publishedWeight(Long breedId) {
        List<StdWeightVersion> list = stdWeightVersionMapper.selectList(Wrappers.<StdWeightVersion>lambdaQuery()
                .eq(StdWeightVersion::getBreedId, breedId)
                .eq(StdWeightVersion::getStatus, "PUBLISHED")
                .orderByDesc(StdWeightVersion::getVersionNo)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private FeedStdVersion publishedFeed(Long breedId) {
        List<FeedStdVersion> list = feedStdVersionMapper.selectList(Wrappers.<FeedStdVersion>lambdaQuery()
                .eq(FeedStdVersion::getBreedId, breedId)
                .eq(FeedStdVersion::getStatus, "PUBLISHED")
                .orderByDesc(FeedStdVersion::getVersionNo)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }
}
