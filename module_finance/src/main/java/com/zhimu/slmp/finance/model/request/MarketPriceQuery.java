package com.zhimu.slmp.finance.model.request;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * API-E31 走势 / API-E32 每日价格查询参数（E11）：不可变，变化即重新请求。
 */
public final class MarketPriceQuery {

    @NonNull
    private final String provinceCode;
    private final long categoryId;
    /** 0 为全部品种 */
    private final long breedId;
    /** SLAUGHTER 出栏价 / SEEDLING 种苗价 */
    @NonNull
    private final String priceType;
    /** 7 / 30 / 90 */
    private final int rangeDays;

    public MarketPriceQuery(@NonNull String provinceCode, long categoryId, long breedId, @NonNull String priceType,
                            int rangeDays) {
        this.provinceCode = provinceCode;
        this.categoryId = categoryId;
        this.breedId = breedId;
        this.priceType = priceType;
        this.rangeDays = rangeDays;
    }

    @NonNull
    public MarketPriceQuery withProvince(@NonNull String code) {
        return new MarketPriceQuery(code, categoryId, breedId, priceType, rangeDays);
    }

    /** 换品类时品种归 0 */
    @NonNull
    public MarketPriceQuery withCategory(long id) {
        return new MarketPriceQuery(provinceCode, id, 0, priceType, rangeDays);
    }

    @NonNull
    public MarketPriceQuery withBreed(long id) {
        return new MarketPriceQuery(provinceCode, categoryId, id, priceType, rangeDays);
    }

    @NonNull
    public MarketPriceQuery withPriceType(@NonNull String type) {
        return new MarketPriceQuery(provinceCode, categoryId, breedId, type, rangeDays);
    }

    @NonNull
    public MarketPriceQuery withRange(int days) {
        return new MarketPriceQuery(provinceCode, categoryId, breedId, priceType, days);
    }

    @NonNull
    public String getProvinceCode() {
        return provinceCode;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public long getBreedId() {
        return breedId;
    }

    @NonNull
    public String getPriceType() {
        return priceType;
    }

    public int getRangeDays() {
        return rangeDays;
    }

    /** 与默认筛选比较时不看区间（区间不影响“恢复默认”） */
    public boolean sameFilter(@NonNull MarketPriceQuery o) {
        return provinceCode.equals(o.provinceCode) && categoryId == o.categoryId && breedId == o.breedId
                && priceType.equals(o.priceType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MarketPriceQuery)) {
            return false;
        }
        MarketPriceQuery q = (MarketPriceQuery) o;
        return sameFilter(q) && rangeDays == q.rangeDays;
    }

    @Override
    public int hashCode() {
        return Objects.hash(provinceCode, categoryId, breedId, priceType, rangeDays);
    }
}
