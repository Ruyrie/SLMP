package com.zhimu.slmp.feed.widget;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 指标卡内容（G01、G04、G06）：标签（可带图标）、数字 + 单位、辅助文字；数字或辅助文字可为 error 色
 * （已逾期餐数、“均为少投 12.5%”、超出范围的“标准 68.6 千克”）。
 */
public final class FeedStatCard {

    @NonNull
    public final String label;
    @NonNull
    public final String value;
    @Nullable
    public final String unit;
    @Nullable
    public final String helper;
    @DrawableRes
    public final int icon;
    public final boolean valueError;
    public final boolean helperError;

    public FeedStatCard(@NonNull String label, @NonNull String value, @Nullable String unit, @Nullable String helper,
                        @DrawableRes int icon, boolean valueError, boolean helperError) {
        this.label = label;
        this.value = value;
        this.unit = unit;
        this.helper = helper;
        this.icon = icon;
        this.valueError = valueError;
        this.helperError = helperError;
    }

    @NonNull
    public static FeedStatCard of(@NonNull String label, @NonNull String value, @Nullable String unit,
                                  @Nullable String helper) {
        return new FeedStatCard(label, value, unit, helper, 0, false, false);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FeedStatCard)) {
            return false;
        }
        FeedStatCard c = (FeedStatCard) o;
        return label.equals(c.label) && value.equals(c.value) && java.util.Objects.equals(unit, c.unit)
                && java.util.Objects.equals(helper, c.helper) && icon == c.icon && valueError == c.valueError
                && helperError == c.helperError;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(label, value, unit, helper, icon, valueError, helperError);
    }
}
