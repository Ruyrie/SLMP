package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-E31 最新价、涨跌、走势、区间统计
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class MarketTrendVO {

    @Nullable
    private LatestPrice latest;
    @Nullable
    private PriceChange change;
    private List<PricePoint> series;
    @Nullable
    private PriceStats stats;
    private boolean empty;

    @Nullable
    public LatestPrice getLatest() {
        return latest;
    }

    @Nullable
    public PriceChange getChange() {
        return change;
    }

    @NonNull
    public List<PricePoint> getSeries() {
        return series == null ? java.util.Collections.emptyList() : series;
    }

    @Nullable
    public PriceStats getStats() {
        return stats;
    }

    public boolean isEmpty() {
        return empty;
    }

    /** 最新价 */
    public static class LatestPrice {

        private BigDecimal price;
        @Nullable
        private String priceUnit;
        private String priceUnitText;
        private String priceDate;

        public BigDecimal getPrice() {
            return price;
        }

        @Nullable
        public String getPriceUnit() {
            return priceUnit;
        }

        public String getPriceUnitText() {
            return priceUnitText;
        }

        public String getPriceDate() {
            return priceDate;
        }
    }

    /** 较上一个有数据的日期 */
    public static class PriceChange {

        @Nullable
        private BigDecimal changeRate;
        /** UP / DOWN / FLAT */
        private String direction;
        @Nullable
        private String compareDate;
        @Nullable
        private BigDecimal comparePrice;

        @Nullable
        public BigDecimal getChangeRate() {
            return changeRate;
        }

        public String getDirection() {
            return direction;
        }

        @Nullable
        public String getCompareDate() {
            return compareDate;
        }

        @Nullable
        public BigDecimal getComparePrice() {
            return comparePrice;
        }
    }

    /** 走势点（price 为空时折线断开） */
    public static class PricePoint {

        private String date;
        @Nullable
        private BigDecimal price;
        private boolean corrected;

        public String getDate() {
            return date;
        }

        @Nullable
        public BigDecimal getPrice() {
            return price;
        }

        public boolean isCorrected() {
            return corrected;
        }
    }

    /** 区间统计 */
    public static class PriceStats {

        @Nullable
        private BigDecimal maxPrice;
        @Nullable
        private String maxDate;
        @Nullable
        private BigDecimal minPrice;
        @Nullable
        private String minDate;
        @Nullable
        private BigDecimal avgPrice;

        @Nullable
        public BigDecimal getMaxPrice() {
            return maxPrice;
        }

        @Nullable
        public String getMaxDate() {
            return maxDate;
        }

        @Nullable
        public BigDecimal getMinPrice() {
            return minPrice;
        }

        @Nullable
        public String getMinDate() {
            return minDate;
        }

        @Nullable
        public BigDecimal getAvgPrice() {
            return avgPrice;
        }
    }
}
