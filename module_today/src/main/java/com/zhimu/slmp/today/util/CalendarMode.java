package com.zhimu.slmp.today.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 养殖日历视图：日、周、月。
 */
public enum CalendarMode {
    DAY,
    WEEK,
    MONTH;

    @NonNull
    public static CalendarMode parse(@Nullable String name, @NonNull CalendarMode fallback) {
        if (name == null) {
            return fallback;
        }
        for (CalendarMode m : values()) {
            if (m.name().equals(name)) {
                return m;
            }
        }
        return fallback;
    }
}
