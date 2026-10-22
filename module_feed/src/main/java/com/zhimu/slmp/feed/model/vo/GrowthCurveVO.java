package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * G06 生长曲线（API-G24）：metrics 无称重时为空；actual_points 第一个为入栏起点（is_entry）；
 * x_min、x_max 为建议横轴（入栏日龄到当前日龄后 15 天）；compare_series 最多 3 条。
 */
public class GrowthCurveVO {

    public static final String STATUS_SLOW = "SLOW";
    public static final String STATUS_FAST = "FAST";
    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_NO_STD = "NO_STD";

    private WeighFormBatchVO batch;
    private String growthStatus;
    private String growthStatusText;
    private GrowthMetricsVO metrics;
    private List<GrowthActualPointVO> actualPoints;
    private Boolean stdAvailable;
    private List<StdPointVO> stdPoints;
    private Integer xMin;
    private Integer xMax;
    private Integer currentDayAge;
    private List<GrowthCompareSeriesVO> compareSeries;
    private Boolean stdUpdatedNotice;

    @Nullable
    public WeighFormBatchVO getBatch() {
        return batch;
    }

    @Nullable
    public String getGrowthStatus() {
        return growthStatus;
    }

    @Nullable
    public String getGrowthStatusText() {
        return growthStatusText;
    }

    @Nullable
    public GrowthMetricsVO getMetrics() {
        return metrics;
    }

    @NonNull
    public List<GrowthActualPointVO> getActualPoints() {
        return actualPoints == null ? Collections.emptyList() : actualPoints;
    }

    public boolean isStdAvailable() {
        return Boolean.TRUE.equals(stdAvailable);
    }

    @NonNull
    public List<StdPointVO> getStdPoints() {
        return stdPoints == null ? Collections.emptyList() : stdPoints;
    }

    @Nullable
    public Integer getXMin() {
        return xMin;
    }

    @Nullable
    public Integer getXMax() {
        return xMax;
    }

    @Nullable
    public Integer getCurrentDayAge() {
        return currentDayAge;
    }

    @NonNull
    public List<GrowthCompareSeriesVO> getCompareSeries() {
        return compareSeries == null ? Collections.emptyList() : compareSeries;
    }

    /** 本地移除对比批次时替换系列（不重新请求） */
    public void setCompareSeries(@Nullable List<GrowthCompareSeriesVO> series) {
        this.compareSeries = series;
    }

    public boolean isStdUpdatedNotice() {
        return Boolean.TRUE.equals(stdUpdatedNotice);
    }

    /** 指标：最近一次称重 */
    public static class GrowthMetricsVO {
        private BigDecimal avgWeight;
        private Integer dayAge;
        private String weighDate;
        private BigDecimal deviationRatio;
        private BigDecimal stdWeight;
        private BigDecimal allowedDeviation;
        private BigDecimal dailyGain;
        private BigDecimal prevDailyGain;
        private String prevGainBaseType;
        private BigDecimal cumulativeGain;

        @Nullable
        public BigDecimal getAvgWeight() {
            return avgWeight;
        }

        @Nullable
        public Integer getDayAge() {
            return dayAge;
        }

        @Nullable
        public String getWeighDate() {
            return weighDate;
        }

        @Nullable
        public BigDecimal getDeviationRatio() {
            return deviationRatio;
        }

        @Nullable
        public BigDecimal getStdWeight() {
            return stdWeight;
        }

        @Nullable
        public BigDecimal getAllowedDeviation() {
            return allowedDeviation;
        }

        @Nullable
        public BigDecimal getDailyGain() {
            return dailyGain;
        }

        @Nullable
        public BigDecimal getPrevDailyGain() {
            return prevDailyGain;
        }

        @Nullable
        public String getPrevGainBaseType() {
            return prevGainBaseType;
        }

        @Nullable
        public BigDecimal getCumulativeGain() {
            return cumulativeGain;
        }
    }

    /** 实际点：date、day_age、avg_weight、std_weight、is_entry */
    public static class GrowthActualPointVO {
        private String date;
        private Integer dayAge;
        private BigDecimal avgWeight;
        private BigDecimal stdWeight;
        private Boolean isEntry;

        @Nullable
        public String getDate() {
            return date;
        }

        public int getDayAge() {
            return dayAge == null ? 0 : dayAge;
        }

        @Nullable
        public BigDecimal getAvgWeight() {
            return avgWeight;
        }

        @Nullable
        public BigDecimal getStdWeight() {
            return stdWeight;
        }

        public boolean isEntry() {
            return Boolean.TRUE.equals(isEntry);
        }
    }

    /** 对比系列：batch_id、batch_no、status、points（day_age、avg_weight） */
    public static class GrowthCompareSeriesVO {
        private Long batchId;
        private String batchNo;
        private String status;
        private List<GrowthComparePointVO> points;

        public long getBatchId() {
            return batchId == null ? 0L : batchId;
        }

        @Nullable
        public String getBatchNo() {
            return batchNo;
        }

        @Nullable
        public String getStatus() {
            return status;
        }

        @NonNull
        public List<GrowthComparePointVO> getPoints() {
            return points == null ? Collections.emptyList() : points;
        }
    }

    /** 对比点：day_age、avg_weight */
    public static class GrowthComparePointVO {
        private Integer dayAge;
        private BigDecimal avgWeight;

        public int getDayAge() {
            return dayAge == null ? 0 : dayAge;
        }

        @Nullable
        public BigDecimal getAvgWeight() {
            return avgWeight;
        }
    }
}
