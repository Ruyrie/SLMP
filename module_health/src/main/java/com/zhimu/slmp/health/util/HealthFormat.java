package com.zhimu.slmp.health.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 本模块的显示格式：间隔符“ · ”、数量与单位、日期（“10月19日”“10月19日 周一”“今天”）、数值文本。
 */
public final class HealthFormat {

    public static final String DOT = " · ";
    public static final String ARROW = " → ";

    private HealthFormat() {
    }

    /** “200 头”（数量与单位之间为不换行空格） */
    @NonNull
    public static String qty(@Nullable Number value, @Nullable String unit) {
        return NumberFormatter.formatQuantity(value, unit);
    }

    /** “10月19日” */
    @NonNull
    public static String date(@Nullable String date) {
        return DateFormatter.formatDate(date, false);
    }

    /** “10月19日 周一” */
    @NonNull
    public static String dateWeek(@Nullable String date) {
        return DateFormatter.formatDate(date, true);
    }

    /** 今天写“今天”，其余“10月20日” */
    @NonNull
    public static String dayText(@Nullable String date) {
        LocalDate d = DateFormatter.parseDate(date);
        if (d != null && d.equals(DateFormatter.today())) {
            return "今天";
        }
        return date(date);
    }

    /** “10月19日 09:10”（不写“今天”，用于实际时间字段） */
    @NonNull
    public static String dateTime(@Nullable String datetime) {
        if (datetime == null || datetime.length() < 16) {
            return datetime == null ? "" : datetime;
        }
        return date(datetime) + " " + datetime.substring(11, 16);
    }

    /** 数值输入框的回填文本：去掉末尾的 0（1.000 → 1） */
    @NonNull
    public static String plain(@Nullable BigDecimal value) {
        if (value == null) {
            return "";
        }
        BigDecimal v = value.stripTrailingZeros();
        if (v.scale() < 0) {
            v = v.setScale(0);
        }
        return v.toPlainString();
    }

    /** 当前养殖场的计数单位：禽类“只”，其余“头” */
    @NonNull
    public static String countUnit(@Nullable FarmContext farmContext) {
        FarmContextVO vo = farmContext == null ? null : farmContext.current();
        return vo != null && "BIRD".equals(vo.getCountUnit()) ? "只" : "头";
    }

    /** 剂量单位“头份 / 头”中的记账部分“头份” */
    @NonNull
    public static String stockPart(@Nullable String doseUnit) {
        if (doseUnit == null) {
            return "";
        }
        int slash = doseUnit.indexOf('/');
        return (slash > 0 ? doseUnit.substring(0, slash) : doseUnit).trim();
    }

    @NonNull
    public static String nonNull(@Nullable String s) {
        return s == null ? "" : s;
    }
}
