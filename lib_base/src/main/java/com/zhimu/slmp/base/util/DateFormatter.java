package com.zhimu.slmp.base.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 日期时间显示（02 号规范 9.3 节）：服务端 date 为 yyyy-MM-dd，datetime 为 yyyy-MM-dd HH:mm:ss（北京时间）。
 * <ul>
 * <li>今天的时刻：“今天 14:20”；本年内：“10月19日 14:20”；跨年：“2025年3月15日 14:20”</li>
 * <li>日期：本年内“10月19日 周一”（可省略星期）；跨年“2027年3月15日”</li>
 * <li>月份：“2026年10月”；日期范围：“10月5日–10月9日”；时长：“3:42”</li>
 * </ul>
 * 解析失败时原样返回输入，null 返回空字符串。
 */
public final class DateFormatter {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] WEEK = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private static Clock clock = Clock.system(ZONE);

    private DateFormatter() {
    }

    /** 单元测试固定“今天” */
    @VisibleForTesting
    public static void setClock(@NonNull Clock testClock) {
        clock = testClock;
    }

    @NonNull
    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    // ===== 解析 =====

    @Nullable
    public static LocalDate parseDate(@Nullable String date) {
        if (date == null || date.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(date.substring(0, 10), DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Nullable
    public static LocalDateTime parseDateTime(@Nullable String datetime) {
        if (datetime == null) {
            return null;
        }
        try {
            if (datetime.length() == 16) {
                return LocalDateTime.parse(datetime + ":00", DATE_TIME);
            }
            return LocalDateTime.parse(datetime.length() > 19 ? datetime.substring(0, 19) : datetime, DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ===== 格式化 =====

    /** datetime：“今天 14:20”“10月19日 14:20”“2025年3月15日 14:20” */
    @NonNull
    public static String formatDateTime(@Nullable String datetime) {
        LocalDateTime t = parseDateTime(datetime);
        if (t == null) {
            return datetime == null ? "" : datetime;
        }
        LocalDate d = t.toLocalDate();
        String hm = t.format(HM);
        if (d.equals(today())) {
            return "今天 " + hm;
        }
        return monthDay(d) + " " + hm;
    }

    /** date 或 datetime 的日期部分：“10月19日 周一”，跨年“2027年3月15日” */
    @NonNull
    public static String formatDate(@Nullable String date) {
        return formatDate(date, true);
    }

    /** @param withWeek 本年内是否带星期（列表位置不够时传 false） */
    @NonNull
    public static String formatDate(@Nullable String date, boolean withWeek) {
        LocalDate d = parseDate(date);
        if (d == null) {
            return date == null ? "" : date;
        }
        return formatDate(d, withWeek);
    }

    @NonNull
    public static String formatDate(@NonNull LocalDate d, boolean withWeek) {
        if (d.getYear() != today().getYear()) {
            return d.getYear() + "年" + d.getMonthValue() + "月" + d.getDayOfMonth() + "日";
        }
        String md = d.getMonthValue() + "月" + d.getDayOfMonth() + "日";
        return withWeek ? md + " " + weekText(d) : md;
    }

    /** “10月19日”，跨年带年份 */
    @NonNull
    public static String monthDay(@NonNull LocalDate d) {
        if (d.getYear() != today().getYear()) {
            return d.getYear() + "年" + d.getMonthValue() + "月" + d.getDayOfMonth() + "日";
        }
        return d.getMonthValue() + "月" + d.getDayOfMonth() + "日";
    }

    /** datetime 的时刻：“09:28” */
    @NonNull
    public static String formatTime(@Nullable String datetimeOrTime) {
        if (datetimeOrTime == null) {
            return "";
        }
        LocalDateTime t = parseDateTime(datetimeOrTime);
        if (t != null) {
            return t.format(HM);
        }
        try {
            String s = datetimeOrTime.length() > 5 ? datetimeOrTime.substring(0, 5) : datetimeOrTime;
            return LocalTime.parse(s, HM).format(HM);
        } catch (DateTimeParseException e) {
            return datetimeOrTime;
        }
    }

    /** 月份标题：“2026年10月”，参数 yyyy-MM 或 yyyy-MM-dd */
    @NonNull
    public static String formatMonth(@Nullable String yearMonth) {
        if (yearMonth == null || yearMonth.length() < 7) {
            return yearMonth == null ? "" : yearMonth;
        }
        try {
            YearMonth ym = YearMonth.parse(yearMonth.substring(0, 7));
            return ym.getYear() + "年" + ym.getMonthValue() + "月";
        } catch (DateTimeParseException e) {
            return yearMonth;
        }
    }

    /** 日期范围：“10月5日–10月9日” */
    @NonNull
    public static String formatDateRange(@Nullable String start, @Nullable String end) {
        return formatDate(start, false) + "–" + formatDate(end, false);
    }

    /** 时长（秒）：“0:18”“3:42”“1:02:05” */
    @NonNull
    public static String formatDuration(int seconds) {
        int s = Math.max(seconds, 0);
        int h = s / 3600;
        int m = (s % 3600) / 60;
        int sec = s % 60;
        if (h > 0) {
            return h + ":" + pad(m) + ":" + pad(sec);
        }
        return m + ":" + pad(sec);
    }

    @NonNull
    public static String weekText(@NonNull LocalDate d) {
        return WEEK[d.getDayOfWeek().getValue() - 1];
    }

    /** 今天的 yyyy-MM-dd，用于请求参数 */
    @NonNull
    public static String todayString() {
        return today().format(DATE);
    }

    @NonNull
    public static String toDateString(@NonNull LocalDate d) {
        return d.format(DATE);
    }

    private static String pad(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
