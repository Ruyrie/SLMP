package com.zhimu.slmp.farm.util;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 计数单位 count_unit 映射：HEAD 为“头”（猪、牛、羊），BIRD 为“只”（禽类）；服务端给了 count_unit_text 时优先用它。
 */
public final class CountUnitText {

    public static final String HEAD = "HEAD";
    public static final String BIRD = "BIRD";
    public static final String TEXT_HEAD = "头";
    public static final String TEXT_BIRD = "只";

    private CountUnitText() {
    }

    @NonNull
    public static String of(@Nullable String countUnit) {
        return BIRD.equals(countUnit) ? TEXT_BIRD : TEXT_HEAD;
    }

    @NonNull
    public static String of(@Nullable String countUnit, @Nullable String countUnitText) {
        return !TextUtils.isEmpty(countUnitText) ? countUnitText : of(countUnit);
    }
}
