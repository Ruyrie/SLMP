package com.zhimu.slmp.stock.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.stock.util.StockCalc;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 本模块的字段校验规则与文案（技术准备文档 2.4 各请求表、UI 文档 2.5）。返回 null 表示通过。 */
public final class StockValidators {

    public static final String MSG_GT_ZERO = "必须大于 0";
    public static final String MSG_SCALE = "最多两位小数";
    public static final BigDecimal MAX_QTY = new BigDecimal("99999999.99");
    public static final BigDecimal MAX_PRICE = new BigDecimal("999999.99");

    private StockValidators() {
    }

    /** 数量 / 单价：必填、大于 0、最多两位小数、不超过上限 */
    @Nullable
    public static String positive(@Nullable String text, @NonNull BigDecimal max) {
        BigDecimal v = StockCalc.parse(text);
        if (v == null || v.signum() <= 0) {
            return MSG_GT_ZERO;
        }
        if (StockCalc.scaleOf(v) > 2) {
            return MSG_SCALE;
        }
        if (v.compareTo(max) > 0) {
            return "不能超过 " + com.zhimu.slmp.base.util.NumberFormatter.formatNumber(max);
        }
        return null;
    }

    /** 头份：必须为整数 */
    @Nullable
    public static String integerDose(@Nullable BigDecimal v) {
        return v != null && !StockCalc.isInteger(v) ? "头份必须是整数" : null;
    }

    /** 日期不晚于今天：“入库日期不能晚于今天” */
    @Nullable
    public static String notAfterToday(@Nullable String date, @NonNull String label) {
        LocalDate d = DateFormatter.parseDate(date);
        if (d == null) {
            return "请选择" + label;
        }
        return d.isAfter(DateFormatter.today()) ? label + "不能晚于今天" : null;
    }

    /** 文字必填且不超过 max 字 */
    @Nullable
    public static String text(@Nullable String text, int max, @NonNull String emptyMessage) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return emptyMessage;
        }
        return t.length() > max ? "最多 " + max + " 个字" : null;
    }

    /** 选填文字不超过 max 字 */
    @Nullable
    public static String optionalText(@Nullable String text, int max) {
        String t = text == null ? "" : text.trim();
        return t.length() > max ? "最多 " + max + " 个字" : null;
    }

    public static boolean isBefore(@Nullable String a, @Nullable String b) {
        LocalDate da = DateFormatter.parseDate(a);
        LocalDate db = DateFormatter.parseDate(b);
        return da != null && db != null && da.isBefore(db);
    }

    public static boolean isAfter(@Nullable String a, @Nullable String b) {
        LocalDate da = DateFormatter.parseDate(a);
        LocalDate db = DateFormatter.parseDate(b);
        return da != null && db != null && da.isAfter(db);
    }
}
