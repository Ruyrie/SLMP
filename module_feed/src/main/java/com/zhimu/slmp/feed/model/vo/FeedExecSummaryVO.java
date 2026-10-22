package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/** G04 执行情况汇总（API-G14）：统计截至昨天；没有在养批次时 has_data 为 false、empty_reason 为 NO_ACTIVE_BATCH。 */
public class FeedExecSummaryVO {

    public static final String EMPTY_NO_ACTIVE_BATCH = "NO_ACTIVE_BATCH";
    public static final String EMPTY_NO_TASK = "NO_TASK";
    public static final String LINE_FLAT = "FLAT";
    public static final String LINE_STEP = "STEP";
    public static final String DIR_LESS = "LESS";
    public static final String DIR_MORE = "MORE";
    public static final String DIR_EQUAL = "EQUAL";

    private String startDate;
    private String endDate;
    private String range;
    private Integer batchCount;
    private Integer plannedMeals;
    private Integer doneMeals;
    private BigDecimal execRate;
    private BigDecimal deviationRatio;
    private String deviationDirection;
    private BigDecimal actualQty;
    private BigDecimal planQty;
    private Integer abnormalCount;
    private Integer underCount;
    private Integer overCount;
    private BigDecimal abnormalSameRatio;
    private String planLineType;
    private BigDecimal planDailyQty;
    private List<FeedExecDailyVO> daily;
    private Boolean hasData;
    private String emptyReason;

    @Nullable
    public String getStartDate() {
        return startDate;
    }

    @Nullable
    public String getEndDate() {
        return endDate;
    }

    @Nullable
    public String getRange() {
        return range;
    }

    public int getBatchCount() {
        return batchCount == null ? 0 : batchCount;
    }

    public int getPlannedMeals() {
        return plannedMeals == null ? 0 : plannedMeals;
    }

    public int getDoneMeals() {
        return doneMeals == null ? 0 : doneMeals;
    }

    @Nullable
    public BigDecimal getExecRate() {
        return execRate;
    }

    @Nullable
    public BigDecimal getDeviationRatio() {
        return deviationRatio;
    }

    @Nullable
    public String getDeviationDirection() {
        return deviationDirection;
    }

    @Nullable
    public BigDecimal getActualQty() {
        return actualQty;
    }

    @Nullable
    public BigDecimal getPlanQty() {
        return planQty;
    }

    public int getAbnormalCount() {
        return abnormalCount == null ? 0 : abnormalCount;
    }

    public int getUnderCount() {
        return underCount == null ? 0 : underCount;
    }

    public int getOverCount() {
        return overCount == null ? 0 : overCount;
    }

    @Nullable
    public BigDecimal getAbnormalSameRatio() {
        return abnormalSameRatio;
    }

    @Nullable
    public String getPlanLineType() {
        return planLineType;
    }

    @Nullable
    public BigDecimal getPlanDailyQty() {
        return planDailyQty;
    }

    @NonNull
    public List<FeedExecDailyVO> getDaily() {
        return daily == null ? Collections.emptyList() : daily;
    }

    public boolean isHasData() {
        return !Boolean.FALSE.equals(hasData);
    }

    @Nullable
    public String getEmptyReason() {
        return emptyReason;
    }

    /** 每日：date、actual_qty、plan_qty、planned_meals、done_meals、deviation_ratio、over_threshold */
    public static class FeedExecDailyVO {
        private String date;
        private BigDecimal actualQty;
        private BigDecimal planQty;
        private Integer plannedMeals;
        private Integer doneMeals;
        private BigDecimal deviationRatio;
        private Boolean overThreshold;

        @Nullable
        public String getDate() {
            return date;
        }

        @Nullable
        public BigDecimal getActualQty() {
            return actualQty;
        }

        @Nullable
        public BigDecimal getPlanQty() {
            return planQty;
        }

        public int getPlannedMeals() {
            return plannedMeals == null ? 0 : plannedMeals;
        }

        public int getDoneMeals() {
            return doneMeals == null ? 0 : doneMeals;
        }

        @Nullable
        public BigDecimal getDeviationRatio() {
            return deviationRatio;
        }

        public boolean isOverThreshold() {
            return Boolean.TRUE.equals(overThreshold);
        }
    }
}
