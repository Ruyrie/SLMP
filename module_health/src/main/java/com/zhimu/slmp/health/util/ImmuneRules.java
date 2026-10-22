package com.zhimu.slmp.health.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 免疫计划规则（UI 文档 1.3 节第 7 条与服务端 2.7 节，状态以服务端返回为准，这里用于文案与界面预估）：
 * <ul>
 *     <li>漏免：待接种或已延期的项目，计划日期过后 7 天仍未登记（今天 − 计划日期 ≥ 7），10月12日的项目从 10月19日起为漏免</li>
 *     <li>日龄：今天日龄 = 入栏日龄 + (今天 − 入栏日期)；届时日龄 = 入栏日龄 + (计划日期 − 入栏日期)</li>
 *     <li>按日龄的计划日期 = 入栏日期 + (接种日龄 − 入栏日龄)</li>
 * </ul>
 */
public final class ImmuneRules {

    /** 计划日期过后 7 天仍未登记标为漏免 */
    public static final int MISSED_DAYS = 7;
    /** 接种日龄上限 */
    public static final int MAX_AGE_DAY = 3650;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_MISSED = "MISSED";
    public static final String STATUS_POSTPONED = "POSTPONED";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String MODE_BY_DAY_AGE = "BY_DAY_AGE";
    public static final String MODE_BY_DATE = "BY_DATE";

    private ImmuneRules() {
    }

    /** 未登记的项目在 today 是否已漏免 */
    public static boolean isMissed(@Nullable LocalDate planDate, @NonNull LocalDate today) {
        return planDate != null && ChronoUnit.DAYS.between(planDate, today) >= MISSED_DAYS;
    }

    /** 从哪一天起标为漏免 */
    @Nullable
    public static LocalDate missedFrom(@Nullable LocalDate planDate) {
        return planDate == null ? null : planDate.plusDays(MISSED_DAYS);
    }

    /** 届时日龄 */
    public static int ageAt(int entryAgeDays, @NonNull LocalDate entryDate, @NonNull LocalDate date) {
        return entryAgeDays + (int) ChronoUnit.DAYS.between(entryDate, date);
    }

    /** 按日龄换算计划日期 */
    @NonNull
    public static LocalDate planDateOf(int entryAgeDays, @NonNull LocalDate entryDate, int planAgeDay) {
        return entryDate.plusDays((long) planAgeDay - entryAgeDays);
    }

    /** “届时 99 日龄”；日期无法解析时返回空串 */
    @NonNull
    public static String ageText(int entryAgeDays, @Nullable String entryDate, @Nullable String date) {
        LocalDate e = DateFormatter.parseDate(entryDate);
        LocalDate d = DateFormatter.parseDate(date);
        if (e == null || d == null) {
            return "";
        }
        return "届时 " + ageAt(entryAgeDays, e, d) + " 日龄";
    }
}
