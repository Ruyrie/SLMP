package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * API-E32 每日价格
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class DailyPriceVO {

    private String priceDate;
    private BigDecimal price;
    @Nullable
    private String priceUnitText;
    @Nullable
    private BigDecimal changeRate;
    @Nullable
    private String direction;
    private boolean corrected;
    @Nullable
    private String correctedAt;

    public String getPriceDate() {
        return priceDate;
    }

    public BigDecimal getPrice() {
        return price;
    }

    @Nullable
    public String getPriceUnitText() {
        return priceUnitText;
    }

    @Nullable
    public BigDecimal getChangeRate() {
        return changeRate;
    }

    @Nullable
    public String getDirection() {
        return direction;
    }

    public boolean isCorrected() {
        return corrected;
    }

    @Nullable
    public String getCorrectedAt() {
        return correctedAt;
    }
}
