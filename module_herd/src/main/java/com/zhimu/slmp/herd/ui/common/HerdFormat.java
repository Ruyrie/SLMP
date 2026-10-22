package com.zhimu.slmp.herd.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.NumberFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 本模块显示格式：数量（千分位 + 单位）、百分比（1 位小数，空为“无数据”）、日期“10月1日”。
 * 所有数值由服务端计算，这里只格式化。
 */
public final class HerdFormat {

    public static final String UNIT_HEAD = "头";
    public static final String UNIT_BIRD = "只";
    public static final String NO_DATA = "无数据";
    public static final String DOT = " · ";

    private HerdFormat() {
    }

    /** 数量单位：unit_text 为空时按头 */
    @NonNull
    public static String unit(@Nullable String unitText) {
        return unitText == null || unitText.isEmpty() ? UNIT_HEAD : unitText;
    }

    /** COUNT_UNIT 编码转单位文字 */
    @NonNull
    public static String unitOfCode(@Nullable String countUnit) {
        return "BIRD".equals(countUnit) ? UNIT_BIRD : UNIT_HEAD;
    }

    /** “1,200 头” */
    @NonNull
    public static String qty(int value, @Nullable String unitText) {
        return NumberFormatter.formatQuantity(value, unit(unitText));
    }

    /** “1,200” */
    @NonNull
    public static String number(int value) {
        return NumberFormatter.formatNumber(value);
    }

    /** “2.4%”；空为“无数据” */
    @NonNull
    public static String percent(@Nullable BigDecimal ratio) {
        return ratio == null ? NO_DATA : NumberFormatter.formatPercent(ratio);
    }

    /** 不带百分号的数值“2.4”（L06 指标行单位单独显示） */
    @NonNull
    public static String percentNumber(@Nullable BigDecimal ratio) {
        if (ratio == null) {
            return NO_DATA;
        }
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    /** “10月1日”（跨年带年份） */
    @NonNull
    public static String date(@Nullable String date) {
        return DateFormatter.formatDate(date, false);
    }

    /** “在栏 150 头 · 容量 220 头” */
    @NonNull
    public static String stockOfCapacity(int stock, int capacity, @Nullable String unitText) {
        return "在栏 " + qty(stock, unitText) + DOT + "容量 " + qty(capacity, unitText);
    }

    /** “200 / 220 头” */
    @NonNull
    public static String stockSlashCapacity(int stock, int capacity, @Nullable String unitText) {
        return number(stock) + " / " + qty(capacity, unitText);
    }

    /** 金额“500 元 / 头”“3.20 元 / 只”：整数不显示小数 */
    @NonNull
    public static String unitPrice(@NonNull BigDecimal price, @Nullable String unitText) {
        BigDecimal p = price.stripTrailingZeros();
        String num = p.scale() <= 0 ? NumberFormatter.formatNumber(p, 0) : NumberFormatter.formatNumber(price, 2);
        return num + NumberFormatter.NBSP + "元 / " + unit(unitText);
    }

    /** 0–1 的比例转为进度（0–100），可大于 100 时截断 */
    public static int progress(@Nullable BigDecimal ratio) {
        if (ratio == null) {
            return 0;
        }
        int p = ratio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, p));
    }

    /** 进度（stock / capacity），容量为 0 时为 0 */
    public static int progress(int stock, int capacity) {
        if (capacity <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, Math.round(stock * 100f / capacity)));
    }

    public static boolean isEmpty(@Nullable CharSequence s) {
        return s == null || s.toString().trim().isEmpty();
    }
}
