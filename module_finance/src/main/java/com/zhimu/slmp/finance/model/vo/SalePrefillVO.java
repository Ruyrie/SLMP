package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-E15 登记预填
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class SalePrefillVO {

    @Nullable
    private MarketPrice marketPrice;
    private List<String> recentBuyers;
    @Nullable
    private String lastBuyer;
    @Nullable
    private String lastPaymentMethod;

    @Nullable
    public MarketPrice getMarketPrice() {
        return marketPrice;
    }

    @NonNull
    public List<String> getRecentBuyers() {
        return recentBuyers == null ? java.util.Collections.emptyList() : recentBuyers;
    }

    @Nullable
    public String getLastBuyer() {
        return lastBuyer;
    }

    @Nullable
    public String getLastPaymentMethod() {
        return lastPaymentMethod;
    }

    /** 当天同地区同品类出栏价 */
    public static class MarketPrice {

        private String provinceName;
        private String categoryName;
        @Nullable
        private BigDecimal price;
        private String priceUnitText;
        @Nullable
        private String priceDate;

        public String getProvinceName() {
            return provinceName;
        }

        public String getCategoryName() {
            return categoryName;
        }

        @Nullable
        public BigDecimal getPrice() {
            return price;
        }

        public String getPriceUnitText() {
            return priceUnitText;
        }

        @Nullable
        public String getPriceDate() {
            return priceDate;
        }
    }
}
