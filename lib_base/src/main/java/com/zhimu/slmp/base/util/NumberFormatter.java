package com.zhimu.slmp.base.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * 数量、金额、百分比显示（02 号规范 9.3 节）：
 * 4 位及以上整数千分位；数字与中文单位之间用不换行空格（U+00A0）避免拆行，拉丁字母单位紧跟数字；
 * 金额两位小数（汇总金额为整数时不显示小数）；百分比 1 位小数；下降用减号“−”（U+2212）。
 */
public final class NumberFormatter {

    /** 数值与单位之间的不换行空格 */
    public static final String NBSP = " ";
    /** 负号使用减号 */
    public static final String MINUS = "−";

    private NumberFormatter() {
    }

    /** “1,200”：千分位；scale 为 null 时去掉末尾的 0 */
    @NonNull
    public static String formatNumber(@Nullable Number value, @Nullable Integer scale) {
        if (value == null) {
            return "";
        }
        BigDecimal v = toBigDecimal(value);
        if (scale != null) {
            v = v.setScale(scale, RoundingMode.HALF_UP);
        } else {
            v = v.stripTrailingZeros();
            if (v.scale() < 0) {
                v = v.setScale(0);
            }
        }
        DecimalFormat df = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.CHINA));
        df.setMinimumFractionDigits(Math.max(v.scale(), 0));
        df.setMaximumFractionDigits(Math.max(v.scale(), 0));
        df.setRoundingMode(RoundingMode.HALF_UP);
        String s = df.format(v);
        return s.startsWith("-") ? MINUS + s.substring(1) : s;
    }

    @NonNull
    public static String formatNumber(@Nullable Number value) {
        return formatNumber(value, null);
    }

    /** “1,200 千克”“200 头”“5MB”：单位为中文时中间加不换行空格 */
    @NonNull
    public static String formatQuantity(@Nullable Number value, @Nullable String unit) {
        return withUnit(formatNumber(value, null), value, unit);
    }

    /** 固定小数位的数量，例如平均体重“60.0 千克” */
    @NonNull
    public static String formatQuantity(@Nullable Number value, int scale, @Nullable String unit) {
        return withUnit(formatNumber(value, scale), value, unit);
    }

    /** 金额两位小数：“3,200.00 元”；unit 为 null 时只返回数字 */
    @NonNull
    public static String formatAmount(@Nullable Number value, @Nullable String unit) {
        return withUnit(formatNumber(value, 2), value, unit);
    }

    /** 金额“3,200.00 元” */
    @NonNull
    public static String formatAmount(@Nullable Number value) {
        return formatAmount(value, "元");
    }

    /** 汇总金额：整数时不显示小数“188,160 元”，否则两位小数 */
    @NonNull
    public static String formatAmountSummary(@Nullable Number value) {
        if (value == null) {
            return "";
        }
        BigDecimal v = toBigDecimal(value);
        boolean whole = v.stripTrailingZeros().scale() <= 0;
        return withUnit(formatNumber(v, whole ? 0 : 2), v, "元");
    }

    /** 比例（0–1）显示为百分比，1 位小数：“92.0%” */
    @NonNull
    public static String formatPercent(@Nullable Number ratio) {
        if (ratio == null) {
            return "";
        }
        return formatNumber(toBigDecimal(ratio).multiply(BigDecimal.valueOf(100)), 1) + "%";
    }

    /** 带正负号的百分比：“+32.1%”“−16.0%” */
    @NonNull
    public static String formatSignedPercent(@Nullable Number ratio) {
        if (ratio == null) {
            return "";
        }
        String s = formatPercent(ratio);
        return toBigDecimal(ratio).signum() > 0 ? "+" + s : s;
    }

    /** 手机号中间 4 位隐藏：“138****6620” */
    @NonNull
    public static String maskPhone(@Nullable String phone) {
        if (phone == null) {
            return "";
        }
        String p = phone.replaceAll("\\s", "");
        if (p.length() != 11) {
            return phone;
        }
        return p.substring(0, 3) + "****" + p.substring(7);
    }

    @NonNull
    private static String withUnit(@NonNull String number, @Nullable Number value, @Nullable String unit) {
        if (value == null) {
            return "";
        }
        if (unit == null || unit.isEmpty()) {
            return number;
        }
        return isLatinUnit(unit) ? number + unit : number + NBSP + unit;
    }

    /** MB、P、x、% 等拉丁字母或符号单位紧跟数字 */
    private static boolean isLatinUnit(@NonNull String unit) {
        char c = unit.charAt(0);
        return c < 0x2E80;
    }

    @NonNull
    private static BigDecimal toBigDecimal(@NonNull Number n) {
        if (n instanceof BigDecimal) {
            return (BigDecimal) n;
        }
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
            return BigDecimal.valueOf(n.longValue());
        }
        return new BigDecimal(n.toString());
    }
}
