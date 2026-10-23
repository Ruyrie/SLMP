package com.zhimu.server.biz.feeding.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 饲喂方案 VO（API-G09–G12）。 */
public final class FeedPlanVO {

    private FeedPlanVO() {
    }

    /** G03 方案 */
    @Data
    public static class Plan {
        private Long planId;
        private Boolean hasPlan;
        private Batch batch;
        private StdCard stdCard;
        private BigDecimal abnormalThreshold;
        private List<Stage> stages = new ArrayList<>();
        private List<FeedRecordVO.OptionBrief> feedOptions = new ArrayList<>();
        private Integer todayGeneratedMeals;
        private Integer version;
    }

    /** 方案批次信息 */
    @Data
    public static class Batch {
        private Long batchId;
        private String batchNo;
        private String penNames;
        private String breedName;
        private Integer dayAge;
        private Integer currentCount;
        private String entryDate;
        private Integer entryAgeDays;
    }

    /** 标准卡 */
    @Data
    public static class StdCard {
        private Boolean stdAvailable;
        private String stdName;
        private String appliedAt;
        private String appliedByName;
        private Integer adjustedCount;
        private Boolean stdUpdated;
    }

    /** 方案阶段 */
    @Data
    public static class Stage {
        private Long stageId;
        private Integer sortNo;
        private String stageName;
        private Integer dayAgeStart;
        private Integer dayAgeEnd;
        private String state;
        private Boolean editable;
        private String execStartDate;
        private String execEndDate;
        private String enterDate;
        private Long feedId;
        private String feedName;
        private Boolean lowStock;
        private BigDecimal dailyQtyPerHead;
        private BigDecimal stdDailyQty;
        private Integer mealsPerDay;
        private List<MealTime> mealTimes = new ArrayList<>();
        private List<String> adjustedFields = new ArrayList<>();
        private BigDecimal todayPlanQty;
        private BigDecimal perMealQty;
        private BigDecimal estimateDailyQty;
    }

    /** 阶段餐次 */
    @Data
    public static class MealTime {
        private Integer mealNo;
        private String mealCode;
        private String mealName;
        private String feedTime;
    }

    /** G03 套用品种标准（只读） */
    @Data
    public static class StdApply {
        private Boolean stdAvailable;
        private Long stdVersionId;
        private String stdName;
        private BigDecimal defaultAbnormalThreshold;
        private List<StdStage> stages = new ArrayList<>();
    }

    /** 标准阶段 */
    @Data
    public static class StdStage {
        private Long stdStageId;
        private String stageName;
        private Integer dayAgeStart;
        private Integer dayAgeEnd;
        private String stdFeedName;
        private String stdFeedTypeCode;
        private Long matchedFeedId;
        private String matchedFeedName;
        private BigDecimal dailyQty;
        private Integer mealsPerDay;
        private List<String> mealTimes = new ArrayList<>();
    }

    /** G01 方案批次选择 */
    @Data
    public static class BatchOption {
        private Long batchId;
        private String batchNo;
        private String penNames;
        private String breedName;
        private Integer dayAge;
        private Boolean hasPlan;
    }
}
