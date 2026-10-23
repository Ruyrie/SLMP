package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** API-U12 品种当前标准体重与饲喂标准；没有标准时 weight / feed 不输出。 */
@Data
public class BreedStandardsVO {

    private Long breedId;
    private WeightStandardVO weight;
    private FeedStandardVO feed;
    /** 只在传 day_age 时输出 */
    private AtDayVO atDay;

    @Data
    public static class WeightStandardVO {
        private Long versionId;
        private Integer versionNo;
        private BigDecimal toleranceRate;
        private Integer suggestedWeighTimes;
        /** 28 ÷ 建议次数取整，2 次 → 14 天 */
        private Integer weighIntervalDays;
        private List<WeightPointVO> points;
    }

    @Data
    public static class WeightPointVO {
        private Integer dayAge;
        private BigDecimal stdWeight;
        private BigDecimal lower;
        private BigDecimal upper;
    }

    @Data
    public static class FeedStandardVO {
        private Long versionId;
        private Integer versionNo;
        private BigDecimal deviationRate;
        private List<FeedStageVO> stages;
    }

    @Data
    public static class FeedStageVO {
        private Long stageId;
        private Integer sortNo;
        private String stageName;
        private String stageShortName;
        private Integer dayAgeStart;
        private Integer dayAgeEnd;
        private String feedTypeCode;
        private String feedTypeText;
        private String suggestedFeedName;
        private BigDecimal dailyFeed;
        private Integer mealsPerDay;
        private List<String> mealTimes;
    }

    @Data
    public static class AtDayVO {
        private Integer dayAge;
        private BigDecimal stdWeight;
        private BigDecimal lower;
        private BigDecimal upper;
        private String stageName;
        private String stageShortName;
    }
}
