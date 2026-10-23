package com.zhimu.server.common.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 北京时间：“今天”“已逾期”“3 天内可直接修改”等全部按这里计算。
 * 业务代码不要直接调用 LocalDate.now()；测试可用 {@link #setClock(Clock)} 注入固定时钟（例如 2026-10-19 周一）。
 */
public final class BeijingTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String TIME_PATTERN = "HH:mm";
    public static final String MONTH_PATTERN = "yyyy-MM";

    public static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(DATE_PATTERN);
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern(TIME_PATTERN);
    public static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern(MONTH_PATTERN);

    private static volatile Clock clock = Clock.system(ZONE);

    private BeijingTime() {
    }

    public static Clock clock() {
        return clock;
    }

    /** 替换时钟（测试或演示环境用）；传 null 恢复系统时钟 */
    public static void setClock(Clock newClock) {
        clock = newClock == null ? Clock.system(ZONE) : newClock.withZone(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    public static LocalTime nowTime() {
        return LocalTime.now(clock);
    }

    /**
     * “登记后 n 天内可直接修改”：登记当天为第 0 天，第 n 天 24:00 前可以修改。
     *
     * @return 截止时刻（第 n+1 天 00:00:00）
     */
    public static LocalDateTime editDeadline(LocalDate registerDate, int days) {
        return registerDate.plusDays(days + 1L).atStartOfDay();
    }

    public static boolean withinEditWindow(LocalDate registerDate, int days) {
        return now().isBefore(editDeadline(registerDate, days));
    }

    public static String format(LocalDateTime time) {
        return time == null ? null : time.format(DATETIME);
    }

    public static String format(LocalDate date) {
        return date == null ? null : date.format(DATE);
    }
}
