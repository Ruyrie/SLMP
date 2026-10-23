package com.zhimu.server.biz.health.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 模块08 的日期、数量显示口径（2.7 节）。
 * <p>
 * “今天 / 明天 / 昨天”按北京日期比较；其余输出“M月d日”；带星期的用于 toast（“11月6日 周五”）。
 * 数量去掉多余的 0（100.000 → 100；0.500 → 0.5）。
 */
public final class HealthFormat {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M月d日");
    private static final String[] WEEK = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private HealthFormat() {
    }

    /** 今天 / 明天 / 昨天 / M月d日 */
    public static String dateText(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate today = com.zhimu.server.common.util.BeijingTime.today();
        if (date.isEqual(today)) {
            return "今天";
        }
        if (date.isEqual(today.plusDays(1))) {
            return "明天";
        }
        if (date.isEqual(today.minusDays(1))) {
            return "昨天";
        }
        return date.format(MONTH_DAY);
    }

    /** 11月6日 周五 */
    public static String dateWithWeek(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(MONTH_DAY) + " " + WEEK[date.getDayOfWeek().getValue() - 1];
    }

    /** 10月5日–10月9日 */
    public static String rangeText(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return null;
        }
        return from.format(MONTH_DAY) + "–" + to.format(MONTH_DAY);
    }

    /** 10月20日至 12月31日 */
    public static String spanText(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return null;
        }
        return from.format(MONTH_DAY) + "至 " + to.format(MONTH_DAY);
    }

    /** HH:mm；秒为 0 时省略 */
    public static String timeText(java.time.LocalTime time) {
        if (time == null) {
            return null;
        }
        return time.getSecond() == 0 && time.getNano() == 0
                ? String.format("%02d:%02d", time.getHour(), time.getMinute())
                : String.format("%02d:%02d:%02d", time.getHour(), time.getMinute(), time.getSecond());
    }

    /** 去掉多余的 0：100.000 → 100 */
    public static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY) : stripped;
    }

    /** 数字文本（不带千分位） */
    public static String numberText(BigDecimal value) {
        return value == null ? null : plain(value).toPlainString();
    }

    /** 解析“1:500”的右侧数值；格式不符返回 null */
    public static Integer concentrationValue(String concentration) {
        if (concentration == null) {
            return null;
        }
        String text = concentration.trim();
        int colon = text.indexOf(':');
        if (colon <= 0 || colon == text.length() - 1) {
            return null;
        }
        String left = text.substring(0, colon);
        String right = text.substring(colon + 1);
        if (!left.chars().allMatch(Character::isDigit) || !right.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            int value = Integer.parseInt(right);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 浓度是否同值（按“1:n”的 n 比较） */
    public static boolean sameConcentration(String left, String right) {
        Integer a = concentrationValue(left);
        Integer b = concentrationValue(right);
        return a != null && a.equals(b);
    }
}
