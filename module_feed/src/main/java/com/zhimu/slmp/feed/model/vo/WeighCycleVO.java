package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.Nullable;

/** 称重周期（API-G19 嵌套、API-G23 返回）：customized 为 false 时是品种建议值。 */
public class WeighCycleVO {

    private Integer perMonth;
    private Integer intervalDays;
    private Boolean customized;
    private Integer suggestedPerMonth;
    private String nextWeighDate;

    @Nullable
    public Integer getPerMonth() {
        return perMonth;
    }

    @Nullable
    public Integer getIntervalDays() {
        return intervalDays;
    }

    public boolean isCustomized() {
        return Boolean.TRUE.equals(customized);
    }

    @Nullable
    public Integer getSuggestedPerMonth() {
        return suggestedPerMonth;
    }

    @Nullable
    public String getNextWeighDate() {
        return nextWeighDate;
    }
}
