package com.zhimu.slmp.health.util;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消毒浓度“1:正整数”（不超过 20 字符）的解析与比较：按 n 的数值比较（1:500 与 1:0500 相同），
 * 与计划浓度不一致时 D08 必须先确认。
 */
public final class ConcentrationUtil {

    public static final int MAX_LENGTH = 20;
    private static final Pattern PATTERN = Pattern.compile("^\\s*1\\s*[:：]\\s*(\\d{1,9})\\s*$");

    private ConcentrationUtil() {
    }

    /** “1:500”中的 500；格式不对时返回 null */
    @Nullable
    public static Integer parse(@Nullable String text) {
        if (text == null || text.length() > MAX_LENGTH) {
            return null;
        }
        Matcher m = PATTERN.matcher(text);
        if (!m.matches()) {
            return null;
        }
        int n = Integer.parseInt(m.group(1));
        return n > 0 ? n : null;
    }

    public static boolean isValid(@Nullable String text) {
        return parse(text) != null;
    }

    /** 统一为“1:n”（中文冒号与空格去掉）；格式不对时原样返回 */
    @Nullable
    public static String normalize(@Nullable String text) {
        Integer n = parse(text);
        return n == null ? text : "1:" + n;
    }

    /** 两个浓度是否不一致；任一方格式不对时不判为不一致（由格式校验提示） */
    public static boolean differs(@Nullable String actual, @Nullable String planned) {
        Integer a = parse(actual);
        Integer p = parse(planned);
        return a != null && p != null && !a.equals(p);
    }
}
