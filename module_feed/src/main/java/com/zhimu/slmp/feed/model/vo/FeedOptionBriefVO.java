package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 饲料选项（API-G03、G05、G10 嵌套）：本场饲料与结存，不含单价、金额；remaining_days 为空即“暂无消耗”，
 * selectable 为假（结存为 0）时不可选，原因见 disabled_reason。
 */
public class FeedOptionBriefVO {

    private Long feedId;
    private String feedName;
    private String unit;
    private BigDecimal stockQty;
    private Integer remainingDays;
    private Boolean lowStock;
    private Boolean selectable;
    private String disabledReason;

    public long getFeedId() {
        return feedId == null ? 0L : feedId;
    }

    @Nullable
    public String getFeedName() {
        return feedName;
    }

    @Nullable
    public String getUnit() {
        return unit;
    }

    @Nullable
    public BigDecimal getStockQty() {
        return stockQty;
    }

    @Nullable
    public Integer getRemainingDays() {
        return remainingDays;
    }

    public boolean isLowStock() {
        return Boolean.TRUE.equals(lowStock);
    }

    /** 没有返回时按可选处理 */
    public boolean isSelectable() {
        return !Boolean.FALSE.equals(selectable);
    }

    @Nullable
    public String getDisabledReason() {
        return disabledReason;
    }
}
