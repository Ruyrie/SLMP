package com.zhimu.server.biz.account.support;

import com.zhimu.server.common.util.BeijingTime;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 账号模块的中文日期时间文案（客户端负责部分拼接，服务端把“10月20日 09:30”等直接写进 message）。 */
public final class DateTimeText {

    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private DateTimeText() {
    }

    public static String datetime(LocalDateTime time) {
        return BeijingTime.format(time);
    }

    public static String date(LocalDate date) {
        return BeijingTime.format(date);
    }

    /** 10月20日 09:30 */
    public static String monthDayTime(LocalDateTime time) {
        return time.getMonthValue() + "月" + time.getDayOfMonth() + "日 " + time.format(HM);
    }

    /** 10月26日 周一 */
    public static String monthDayWeek(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日 " + weekday(date.getDayOfWeek());
    }

    /** 10月26日 */
    public static String monthDay(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    /** 11月18日 */
    public static String monthDayCn(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    public static String weekday(DayOfWeek dayOfWeek) {
        return WEEKDAYS[dayOfWeek.getValue() - 1];
    }
}
