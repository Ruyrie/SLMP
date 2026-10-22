package com.zhimu.slmp.inspect.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;

/**
 * 读取页面参数：推送、消息、待办的 params 为字符串（"2001"），页面内跳转为 long，两种都接受。
 */
public final class Args {

    private Args() {
    }

    @Nullable
    public static Long getLong(@NonNull SavedStateHandle h, @NonNull String key) {
        return toLong(h.get(key));
    }

    @Nullable
    public static Long toLong(@Nullable Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        if (v instanceof String) {
            try {
                return Long.parseLong(((String) v).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    public static Integer getInt(@NonNull SavedStateHandle h, @NonNull String key) {
        Long l = toLong(h.get(key));
        return l == null ? null : l.intValue();
    }

    @Nullable
    public static String getString(@NonNull SavedStateHandle h, @NonNull String key) {
        Object v = h.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static boolean getBoolean(@NonNull SavedStateHandle h, @NonNull String key) {
        Object v = h.get(key);
        return Boolean.TRUE.equals(v) || "true".equals(v);
    }
}
