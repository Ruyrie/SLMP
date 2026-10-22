package com.zhimu.slmp.herd.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 批次号格式：去空格转大写后为 ZM + 4 位数字 + “-” + 3 位及以上数字，例如 ZM2610-031。
 */
public final class BatchNoValidator {

    public static final String MSG_EMPTY = "请输入批次号";
    public static final String MSG_INVALID = "批次号格式不正确，例如 ZM2610-031";

    private static final Pattern PATTERN = Pattern.compile("^ZM\\d{4}-\\d{3,}$");

    private BatchNoValidator() {
    }

    /** 去掉全部空白并转大写 */
    @NonNull
    public static String normalize(@Nullable String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(@Nullable String input) {
        return PATTERN.matcher(normalize(input)).matches();
    }

    /** 本地校验：通过返回 null，否则返回提示文案 */
    @Nullable
    public static String validate(@Nullable String input) {
        String s = normalize(input);
        if (s.isEmpty()) {
            return MSG_EMPTY;
        }
        return PATTERN.matcher(s).matches() ? null : MSG_INVALID;
    }
}
