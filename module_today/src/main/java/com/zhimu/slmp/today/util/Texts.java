package com.zhimu.slmp.today.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;

/**
 * 纯 Java 的文字工具（ViewModel 中使用，单元测试不依赖 android.text.TextUtils）。
 */
public final class Texts {

    /** “·”分隔符：前面用不换行空格，换行不放在“·”前 */
    public static final String DOT = " · ";

    private Texts() {
    }

    public static boolean isEmpty(@Nullable CharSequence s) {
        return s == null || s.length() == 0;
    }

    public static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    @NonNull
    public static String orEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }

    /** 用 sep 连接非空项 */
    @NonNull
    public static String join(@NonNull String sep, @NonNull Collection<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (isEmpty(p)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(p);
        }
        return sb.toString();
    }
}
