package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 分类占比（E02 环形图与图例）
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class CategoryShareVO {

    private long categoryId;
    private String name;
    @Nullable
    private String icon;
    private BigDecimal amount;
    private BigDecimal shareRate;
    @Nullable
    private BigDecimal momRate;
    private boolean comparable;
    /** UP / DOWN / FLAT */
    @Nullable
    private String trend;

    public long getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getIcon() {
        return icon;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getShareRate() {
        return shareRate;
    }

    @Nullable
    public BigDecimal getMomRate() {
        return momRate;
    }

    public boolean isComparable() {
        return comparable;
    }

    @Nullable
    public String getTrend() {
        return trend;
    }
}
