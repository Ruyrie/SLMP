package com.zhimu.slmp.feed.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 按投喂时间命名餐次（与服务端 2.7.2 一致）：10:00 前早餐，10:00–14:59 午餐，15:00 起晚餐；
 * 同一阶段内名称重复时后一餐为加餐。
 */
public final class MealNameResolver {

    public static final String BREAKFAST = "BREAKFAST";
    public static final String LUNCH = "LUNCH";
    public static final String DINNER = "DINNER";
    public static final String EXTRA = "EXTRA";

    /** 餐次增加时依次尝试的默认时间 */
    private static final String[] DEFAULT_TIMES = {"07:00", "11:30", "17:00", "20:00", "09:00", "14:00", "19:00"};

    private MealNameResolver() {
    }

    /** 餐次编码；时间格式不对时为 null */
    @Nullable
    public static String codeOf(@Nullable String hhmm) {
        int minutes = minutesOf(hhmm);
        if (minutes < 0) {
            return null;
        }
        if (minutes < 10 * 60) {
            return BREAKFAST;
        }
        if (minutes < 15 * 60) {
            return LUNCH;
        }
        return DINNER;
    }

    @NonNull
    public static String nameOf(@Nullable String code) {
        if (BREAKFAST.equals(code)) {
            return "早餐";
        }
        if (LUNCH.equals(code)) {
            return "午餐";
        }
        if (DINNER.equals(code)) {
            return "晚餐";
        }
        return "加餐";
    }

    /** 已排序的投喂时间依次命名，重复的为加餐 */
    @NonNull
    public static List<String> names(@NonNull List<String> sortedTimes) {
        List<String> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String t : sortedTimes) {
            String code = codeOf(t);
            if (code == null || used.contains(code)) {
                out.add(nameOf(EXTRA));
            } else {
                used.add(code);
                out.add(nameOf(code));
            }
        }
        return out;
    }

    /** HH:mm 转为当天分钟数；格式不对时为 -1 */
    public static int minutesOf(@Nullable String hhmm) {
        if (hhmm == null || !hhmm.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            return -1;
        }
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }

    /** 餐次增加时追加的默认时间：取第一个未被使用的候选 */
    @NonNull
    public static String nextDefaultTime(@NonNull List<String> existing) {
        for (String t : DEFAULT_TIMES) {
            if (!existing.contains(t)) {
                return t;
            }
        }
        return "12:00";
    }

    /** 按餐次数给出默认时间 */
    @NonNull
    public static List<String> defaultTimes(int meals) {
        List<String> list = new ArrayList<>();
        switch (meals) {
            case 1:
                list.add("07:00");
                break;
            case 2:
                list.add("07:00");
                list.add("17:00");
                break;
            case 4:
                list.add("07:00");
                list.add("11:00");
                list.add("15:00");
                list.add("19:00");
                break;
            default:
                list.add("07:00");
                list.add("11:30");
                list.add("17:00");
                break;
        }
        return list;
    }
}
