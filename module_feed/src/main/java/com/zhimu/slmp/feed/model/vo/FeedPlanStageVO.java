package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/** 方案阶段（FeedPlanVO.stages）：state 为 ENDED / CURRENT / UPCOMING，已结束阶段 editable 为 false。 */
public class FeedPlanStageVO {

    public static final String STATE_ENDED = "ENDED";
    public static final String STATE_CURRENT = "CURRENT";
    public static final String STATE_UPCOMING = "UPCOMING";

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
    private List<FeedMealTimeVO> mealTimes;
    private List<String> adjustedFields;
    private BigDecimal todayPlanQty;
    private BigDecimal perMealQty;
    private BigDecimal estimateDailyQty;

    @Nullable
    public Long getStageId() {
        return stageId;
    }

    @Nullable
    public Integer getSortNo() {
        return sortNo;
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
    public String getState() {
        return state;
    }

    public boolean isEditable() {
        return !Boolean.FALSE.equals(editable);
    }

    @Nullable
    public String getExecStartDate() {
        return execStartDate;
    }

    @Nullable
    public String getExecEndDate() {
        return execEndDate;
    }

    @Nullable
    public String getEnterDate() {
        return enterDate;
    }

    @Nullable
    public Long getFeedId() {
        return feedId;
    }

    @Nullable
    public String getFeedName() {
        return feedName;
    }

    public boolean isLowStock() {
        return Boolean.TRUE.equals(lowStock);
    }

    @Nullable
    public BigDecimal getDailyQtyPerHead() {
        return dailyQtyPerHead;
    }

    @Nullable
    public BigDecimal getStdDailyQty() {
        return stdDailyQty;
    }

    @Nullable
    public Integer getMealsPerDay() {
        return mealsPerDay;
    }

    @NonNull
    public List<FeedMealTimeVO> getMealTimes() {
        return mealTimes == null ? Collections.emptyList() : mealTimes;
    }

    @NonNull
    public List<String> getAdjustedFields() {
        return adjustedFields == null ? Collections.emptyList() : adjustedFields;
    }

    @Nullable
    public BigDecimal getTodayPlanQty() {
        return todayPlanQty;
    }

    @Nullable
    public BigDecimal getPerMealQty() {
        return perMealQty;
    }

    @Nullable
    public BigDecimal getEstimateDailyQty() {
        return estimateDailyQty;
    }

    /** 餐次：meal_no、meal_code、meal_name、feed_time（HH:mm） */
    public static class FeedMealTimeVO {
        private Integer mealNo;
        private String mealCode;
        private String mealName;
        private String feedTime;

        @Nullable
        public Integer getMealNo() {
            return mealNo;
        }

        @Nullable
        public String getMealCode() {
            return mealCode;
        }

        @Nullable
        public String getMealName() {
            return mealName;
        }

        @Nullable
        public String getFeedTime() {
            return feedTime;
        }
    }
}
