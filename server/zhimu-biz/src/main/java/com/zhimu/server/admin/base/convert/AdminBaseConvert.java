package com.zhimu.server.admin.base.convert;

import com.zhimu.server.admin.base.entity.Breed;
import com.zhimu.server.admin.base.entity.Category;
import com.zhimu.server.admin.base.entity.DictItem;
import com.zhimu.server.admin.base.entity.DictItemScope;
import com.zhimu.server.admin.base.entity.FeedStdStage;
import com.zhimu.server.admin.base.entity.FeedStdVersion;
import com.zhimu.server.admin.base.entity.StdWeightPoint;
import com.zhimu.server.admin.base.entity.StdWeightVersion;
import com.zhimu.server.admin.base.support.StdCalculator;
import com.zhimu.server.admin.base.vo.BaseCategoryBreedVO;
import com.zhimu.server.admin.base.vo.BaseCategoryVO;
import com.zhimu.server.admin.base.vo.BaseDictItemVO;
import com.zhimu.server.admin.base.vo.BreedDetailVO;
import com.zhimu.server.admin.base.vo.BreedRefVO;
import com.zhimu.server.admin.base.vo.BreedVO;
import com.zhimu.server.admin.base.vo.CategoryNodeVO;
import com.zhimu.server.admin.base.vo.CategoryVO;
import com.zhimu.server.admin.base.vo.DictItemVO;
import com.zhimu.server.admin.base.vo.DictScopeVO;
import com.zhimu.server.admin.base.vo.FeedStdStageVO;
import com.zhimu.server.admin.base.vo.FeedStdVersionVO;
import com.zhimu.server.admin.base.vo.StdVersionVO;
import com.zhimu.server.admin.base.vo.StdWeightPointVO;
import com.zhimu.server.admin.base.vo.StdWeightVersionVO;
import com.zhimu.server.common.enums.BaseStatus;
import com.zhimu.server.common.enums.CountUnit;
import com.zhimu.server.common.enums.Species;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 基础数据实体 → VO（手工转换，不引入 MapStruct 的隐式字段丢失）。 */
public final class AdminBaseConvert {

    private AdminBaseConvert() {
    }

    public static String speciesText(String species) {
        if (species == null) {
            return null;
        }
        try {
            return Species.valueOf(species).getText();
        } catch (IllegalArgumentException e) {
            return species;
        }
    }

    public static String countUnitText(String countUnit) {
        if (countUnit == null) {
            return null;
        }
        try {
            return CountUnit.valueOf(countUnit).getText();
        } catch (IllegalArgumentException e) {
            return countUnit;
        }
    }

    public static String statusText(String status) {
        if (status == null) {
            return null;
        }
        try {
            return BaseStatus.valueOf(status).getText();
        } catch (IllegalArgumentException e) {
            return status;
        }
    }

    public static String versionStatusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "DRAFT" -> "未发布";
            case "SCHEDULED" -> "待生效";
            case "PUBLISHED" -> "线上";
            case "SUPERSEDED" -> "历史";
            default -> status;
        };
    }

    public static String changeTypeText(String changeType) {
        if (changeType == null) {
            return null;
        }
        return switch (changeType) {
            case "NONE" -> "未变";
            case "ADDED" -> "新增";
            case "MODIFIED" -> "已修改";
            case "DELETED" -> "已删除";
            default -> changeType;
        };
    }

    public static CategoryVO category(Category entity) {
        CategoryVO vo = new CategoryVO();
        fillCategory(vo, entity);
        return vo;
    }

    static void fillCategory(CategoryVO vo, Category entity) {
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setSpecies(entity.getSpecies());
        vo.setSpeciesText(speciesText(entity.getSpecies()));
        vo.setCountUnit(entity.getCountUnit());
        vo.setCountUnitText(countUnitText(entity.getCountUnit()));
        vo.setIsBreeding(Boolean.TRUE.equals(entity.getIsBreeding()));
        vo.setStatus(entity.getStatus());
        vo.setStatusText(statusText(entity.getStatus()));
        vo.setDisableReason(entity.getDisableReason());
        vo.setSortNo(entity.getSortNo());
        vo.setVersion(entity.getVersion());
    }

    public static CategoryNodeVO categoryNode(Category entity, int breedCount) {
        CategoryNodeVO vo = new CategoryNodeVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setSpecies(entity.getSpecies());
        vo.setSpeciesText(speciesText(entity.getSpecies()));
        vo.setCountUnit(entity.getCountUnit());
        vo.setCountUnitText(countUnitText(entity.getCountUnit()));
        vo.setIsBreeding(Boolean.TRUE.equals(entity.getIsBreeding()));
        vo.setStatus(entity.getStatus());
        vo.setStatusText(statusText(entity.getStatus()));
        vo.setSortNo(entity.getSortNo());
        vo.setBreedCount(breedCount);
        return vo;
    }

    public static CategoryNodeVO.BreedNodeVO breedNode(Breed entity, boolean hasStdWeight, boolean hasFeedStd,
                                                       boolean hasImmuneTemplate) {
        CategoryNodeVO.BreedNodeVO vo = new CategoryNodeVO.BreedNodeVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setStatus(entity.getStatus());
        vo.setStatusText(statusText(entity.getStatus()));
        vo.setSortNo(entity.getSortNo());
        vo.setGestationDays(entity.getGestationDays());
        vo.setIncubationDays(entity.getIncubationDays());
        vo.setHasStdWeight(hasStdWeight);
        vo.setHasFeedStd(hasFeedStd);
        vo.setHasImmuneTemplate(hasImmuneTemplate);
        return vo;
    }

    public static BreedVO breed(Breed entity, Category category) {
        BreedVO vo = new BreedVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setCategoryId(entity.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getName());
        vo.setGestationDays(entity.getGestationDays());
        vo.setIncubationDays(entity.getIncubationDays());
        vo.setPregnancyCheckDays(entity.getPregnancyCheckDays());
        vo.setOffspringBreedId(entity.getOffspringBreedId());
        vo.setStatus(entity.getStatus());
        vo.setStatusText(statusText(entity.getStatus()));
        vo.setDisableReason(entity.getDisableReason());
        vo.setSortNo(entity.getSortNo());
        vo.setVersion(entity.getVersion());
        return vo;
    }

    public static BreedDetailVO breedDetail(Breed entity, Category category, CategoryVO categoryVO, Breed offspring) {
        BreedDetailVO vo = new BreedDetailVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setCategory(categoryVO);
        vo.setGestationDays(entity.getGestationDays());
        vo.setIncubationDays(entity.getIncubationDays());
        vo.setPregnancyCheckDays(entity.getPregnancyCheckDays());
        vo.setOffspringBreed(offspring == null ? null : new BreedRefVO(offspring.getId(), offspring.getName()));
        vo.setStatus(entity.getStatus());
        vo.setStatusText(statusText(entity.getStatus()));
        vo.setDisableReason(entity.getDisableReason());
        vo.setActiveBatchCount(0);
        vo.setFarmCount(0);
        vo.setBreedingAnimalCount(0);
        vo.setLastModifiedAt(entity.getUpdatedAt());
        vo.setVersion(entity.getVersion());
        return vo;
    }

    public static BaseCategoryVO baseCategory(Category entity) {
        BaseCategoryVO vo = new BaseCategoryVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setSpecies(entity.getSpecies());
        vo.setSpeciesText(speciesText(entity.getSpecies()));
        vo.setCountUnit(entity.getCountUnit());
        vo.setCountUnitText(countUnitText(entity.getCountUnit()));
        vo.setIsBreeding(Boolean.TRUE.equals(entity.getIsBreeding()));
        return vo;
    }

    public static BaseCategoryBreedVO baseBreed(Breed entity, boolean hasStdWeight, boolean hasFeedStd,
                                                boolean hasImmuneTemplate) {
        BaseCategoryBreedVO vo = new BaseCategoryBreedVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setGestationDays(entity.getGestationDays());
        vo.setIncubationDays(entity.getIncubationDays());
        vo.setPregnancyCheckDays(entity.getPregnancyCheckDays());
        vo.setOffspringBreedId(entity.getOffspringBreedId());
        vo.setHasStdWeight(hasStdWeight);
        vo.setHasFeedStd(hasFeedStd);
        vo.setHasImmuneTemplate(hasImmuneTemplate);
        return vo;
    }

    public static StdWeightVersionVO stdWeightVersion(StdWeightVersion version, List<StdWeightPoint> points,
                                                      BigDecimal toleranceRate,
                                                      Map<Integer, BigDecimal> onlineWeights, boolean draft) {
        StdWeightVersionVO vo = new StdWeightVersionVO();
        vo.setVersionId(version.getId());
        vo.setVersionNo(version.getVersionNo());
        vo.setStatus(version.getStatus());
        vo.setStatusText(versionStatusText(version.getStatus()));
        vo.setToleranceRate(toleranceRate);
        vo.setSuggestedWeighTimes(version.getSuggestedWeighTimes());
        vo.setEffectiveMode(version.getEffectiveMode());
        vo.setEffectiveDate(version.getEffectiveDate());
        vo.setEffectiveAt(version.getEffectiveAt());
        vo.setUpdateNote(version.getUpdateNote());
        vo.setPublishedAt(version.getPublishedAt());
        vo.setRecalculatedAt(version.getRecalculatedAt());
        if (draft) {
            vo.setVersion(version.getVersion());
        }
        List<StdWeightPoint> sorted = StdCalculator.sorted(points);
        int changed = 0;
        for (int i = 0; i < sorted.size(); i++) {
            StdWeightPoint point = sorted.get(i);
            StdWeightPointVO pointVO = new StdWeightPointVO();
            pointVO.setDayAge(point.getDayAge());
            pointVO.setStdWeight(StdCalculator.round1(point.getStdWeight()));
            pointVO.setLower(StdCalculator.lower(point.getStdWeight(), toleranceRate));
            pointVO.setUpper(StdCalculator.upper(point.getStdWeight(), toleranceRate));
            pointVO.setDailyGain(StdCalculator.dailyGain(sorted, i));
            if (draft) {
                BigDecimal online = onlineWeights == null ? null : onlineWeights.get(point.getDayAge());
                pointVO.setOnlineWeight(online);
                if (online != null) {
                    BigDecimal diff = StdCalculator.round1(point.getStdWeight().subtract(online));
                    pointVO.setDiff(diff);
                    boolean isChanged = diff.compareTo(BigDecimal.ZERO) != 0;
                    pointVO.setChanged(isChanged);
                    if (isChanged) {
                        changed++;
                    }
                } else {
                    pointVO.setChanged(true);
                    changed++;
                }
            }
            vo.getPoints().add(pointVO);
        }
        if (draft) {
            vo.setChangedCount(changed);
        }
        return vo;
    }

    public static FeedStdVersionVO feedStdVersion(FeedStdVersion version, List<FeedStdStage> stages,
                                                  Map<Integer, FeedStdStage> onlineBySort, boolean draft) {
        FeedStdVersionVO vo = new FeedStdVersionVO();
        vo.setVersionId(version.getId());
        vo.setVersionNo(version.getVersionNo());
        vo.setStatus(version.getStatus());
        vo.setStatusText(versionStatusText(version.getStatus()));
        vo.setDeviationRate(version.getDeviationRate());
        vo.setUpdateNote(version.getUpdateNote());
        vo.setPublishedAt(version.getPublishedAt());
        if (draft) {
            vo.setVersion(version.getVersion());
        } else {
            vo.setAppliedPlanCount(0);
        }
        List<FeedStdStage> sorted = new ArrayList<>(stages == null ? List.of() : stages);
        sorted.sort(java.util.Comparator.comparing(FeedStdStage::getSortNo));
        for (FeedStdStage stage : sorted) {
            FeedStdStageVO stageVO = feedStage(stage);
            if (draft) {
                FeedStdStage online = onlineBySort == null ? null : onlineBySort.get(stage.getSortNo());
                stageVO.setChanged(!StdCalculator.sameStage(stage, online));
            }
            vo.getStages().add(stageVO);
        }
        return vo;
    }

    public static FeedStdStageVO feedStage(FeedStdStage stage) {
        FeedStdStageVO vo = new FeedStdStageVO();
        vo.setStageId(stage.getId());
        vo.setSortNo(stage.getSortNo());
        vo.setStageName(stage.getStageName());
        vo.setStageShortName(stage.getStageShortName());
        vo.setDayAgeStart(stage.getDayAgeStart());
        vo.setDayAgeEnd(stage.getDayAgeEnd());
        vo.setFeedTypeCode(stage.getFeedTypeCode());
        vo.setSuggestedFeedName(stage.getSuggestedFeedName());
        vo.setDailyFeed(stage.getDailyFeed());
        vo.setMealsPerDay(stage.getMealsPerDay());
        vo.setMealTimes(splitMealTimes(stage.getMealTimes()));
        return vo;
    }

    public static StdVersionVO stdWeightVersionRecord(StdWeightVersion version, List<StdWeightPoint> points,
                                                      BigDecimal toleranceRate) {
        StdVersionVO vo = new StdVersionVO();
        vo.setVersionId(version.getId());
        vo.setVersionNo(version.getVersionNo());
        vo.setStatus(version.getStatus());
        vo.setStatusText(versionStatusText(version.getStatus()));
        vo.setEffectiveMode(version.getEffectiveMode());
        vo.setEffectiveDate(version.getEffectiveDate());
        vo.setUpdateNote(version.getUpdateNote());
        vo.setRollbackFromNo(version.getRollbackFromNo());
        vo.setPublishedAt(version.getPublishedAt());
        vo.setToleranceRate(toleranceRate);
        vo.setSuggestedWeighTimes(version.getSuggestedWeighTimes());
        List<StdWeightPoint> sorted = StdCalculator.sorted(points);
        List<StdWeightPointVO> pointVOs = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            StdWeightPoint p = sorted.get(i);
            StdWeightPointVO pv = new StdWeightPointVO();
            pv.setDayAge(p.getDayAge());
            pv.setStdWeight(StdCalculator.round1(p.getStdWeight()));
            pv.setLower(StdCalculator.lower(p.getStdWeight(), toleranceRate));
            pv.setUpper(StdCalculator.upper(p.getStdWeight(), toleranceRate));
            pv.setDailyGain(StdCalculator.dailyGain(sorted, i));
            pointVOs.add(pv);
        }
        vo.setPoints(pointVOs);
        return vo;
    }

    public static StdVersionVO feedStdVersionRecord(FeedStdVersion version, List<FeedStdStage> stages) {
        StdVersionVO vo = new StdVersionVO();
        vo.setVersionId(version.getId());
        vo.setVersionNo(version.getVersionNo());
        vo.setStatus(version.getStatus());
        vo.setStatusText(versionStatusText(version.getStatus()));
        vo.setUpdateNote(version.getUpdateNote());
        vo.setRollbackFromNo(version.getRollbackFromNo());
        vo.setPublishedAt(version.getPublishedAt());
        vo.setDeviationRate(version.getDeviationRate());
        List<FeedStdStageVO> stageVOs = new ArrayList<>();
        List<FeedStdStage> sorted = new ArrayList<>(stages == null ? List.of() : stages);
        sorted.sort(java.util.Comparator.comparing(FeedStdStage::getSortNo));
        for (FeedStdStage stage : sorted) {
            FeedStdStageVO sv = feedStage(stage);
            sv.setChanged(false);
            stageVOs.add(sv);
        }
        vo.setStages(stageVOs);
        return vo;
    }

    public static DictItemVO dictItem(DictItem entity, List<DictItemScope> scopes, Map<Long, String> categoryNames) {
        DictItemVO vo = new DictItemVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setIcon(entity.getIcon());
        vo.setRemarkRequired(Boolean.TRUE.equals(entity.getRemarkRequired()));
        vo.setIsFallback(Boolean.TRUE.equals(entity.getIsFallback()));
        vo.setIsSystem(Boolean.TRUE.equals(entity.getIsSystem()));
        vo.setSortNo(entity.getSortNo());
        vo.setStatus(entity.getStatus());
        vo.setStatusText(statusText(entity.getStatus()));
        vo.setDisableReason(entity.getDisableReason());
        vo.setUsageCount(0);
        vo.setVersion(entity.getVersion());
        List<DictScopeVO> scopeVOs = new ArrayList<>();
        for (DictItemScope scope : scopes == null ? List.<DictItemScope>of() : scopes) {
            DictScopeVO sv = new DictScopeVO();
            sv.setScopeType(scope.getScopeType());
            sv.setSpecies(emptyToNull(scope.getSpecies()));
            sv.setCategoryId(scope.getCategoryId() == null || scope.getCategoryId() == 0 ? null : scope.getCategoryId());
            if (scope.getCategoryId() != null && scope.getCategoryId() != 0) {
                sv.setCategoryName(categoryNames == null ? null : categoryNames.get(scope.getCategoryId()));
            }
            scopeVOs.add(sv);
        }
        vo.setScopes(scopeVOs);
        return vo;
    }

    public static BaseDictItemVO baseDictItem(DictItem entity) {
        BaseDictItemVO vo = new BaseDictItemVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setRemarkRequired(Boolean.TRUE.equals(entity.getRemarkRequired()));
        vo.setIsSystem(Boolean.TRUE.equals(entity.getIsSystem()));
        vo.setSortNo(entity.getSortNo());
        return vo;
    }

    public static List<String> splitMealTimes(String mealTimes) {
        if (mealTimes == null || mealTimes.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.stream(mealTimes.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).toList());
    }

    public static String joinMealTimes(List<String> mealTimes) {
        if (mealTimes == null || mealTimes.isEmpty()) {
            return null;
        }
        return String.join(",", mealTimes.stream().map(String::trim).toList());
    }

    /** 分类 ID → 名称，供字典范围显示 */
    public static Map<Long, String> categoryNames(List<Category> categories) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Category category : categories == null ? List.<Category>of() : categories) {
            map.put(category.getId(), category.getName());
        }
        return map;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
