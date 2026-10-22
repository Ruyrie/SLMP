package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 套用品种标准（API-G11，只读不落库）：无标准时 std_available 为 false；meal_times 暂按 HH:mm 字符串处理（待定事项 3）。
 */
public class FeedStdApplyVO {

    private Boolean stdAvailable;
    private Long stdVersionId;
    private String stdName;
    private BigDecimal defaultAbnormalThreshold;
    private List<FeedStdStageVO> stages;

    public boolean isStdAvailable() {
        return Boolean.TRUE.equals(stdAvailable);
    }

    @Nullable
    public Long getStdVersionId() {
        return stdVersionId;
    }

    @Nullable
    public String getStdName() {
        return stdName;
    }

    @Nullable
    public BigDecimal getDefaultAbnormalThreshold() {
        return defaultAbnormalThreshold;
    }

    @NonNull
    public List<FeedStdStageVO> getStages() {
        return stages == null ? Collections.emptyList() : stages;
    }

    /** 标准阶段：matched_feed_id 匹配不到本场饲料时为空 */
    public static class FeedStdStageVO {
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
        private List<String> mealTimes;

        @Nullable
        public Long getStdStageId() {
            return stdStageId;
        }

        @Nullable
        public String getStageName() {
            return stageName;
        }

        @Nullable
        public Integer getDayAgeStart() {
            return dayAgeStart;
        }

        @Nullable
        public Integer getDayAgeEnd() {
            return dayAgeEnd;
        }

        @Nullable
        public String getStdFeedName() {
            return stdFeedName;
        }

        @Nullable
        public String getStdFeedTypeCode() {
            return stdFeedTypeCode;
        }

        @Nullable
        public Long getMatchedFeedId() {
            return matchedFeedId;
        }

        @Nullable
        public String getMatchedFeedName() {
            return matchedFeedName;
        }

        @Nullable
        public BigDecimal getDailyQty() {
            return dailyQty;
        }

        @Nullable
        public Integer getMealsPerDay() {
            return mealsPerDay;
        }

        @NonNull
        public List<String> getMealTimes() {
            return mealTimes == null ? Collections.emptyList() : mealTimes;
        }
    }
}
