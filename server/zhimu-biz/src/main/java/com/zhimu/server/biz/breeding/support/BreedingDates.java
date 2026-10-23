package com.zhimu.server.biz.breeding.support;

import com.zhimu.server.common.util.BeijingTime;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 模块05 的北京时间计算（模块05 文档 2.7.1、2.7.3 节）。 */
public final class BreedingDates {

    public static final int REMIND_BEFORE_DAYS = 3;

    private BreedingDates() {
    }

    public static LocalDate today() {
        return BeijingTime.today();
    }

    public static int daysBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(from, to);
    }

    /** 距预产 / 距出雏：目标日 − 今天，可为负 */
    public static int daysFromToday(LocalDate target) {
        return target == null ? 0 : daysBetween(today(), target);
    }

    public static LocalDate plusDays(LocalDate date, int days) {
        return date == null ? null : date.plusDays(days);
    }

    /**
     * 提醒状态文字（模块05 文档 2.7.3）：两次都已发“已提醒 10月16日、今天”；今天发了提前提醒
     * “今天已发提前 3 天提醒”；都未到“10月23日提醒”。
     */
    public static String reminderText(LocalDate remindBeforeDate, LocalDate remindDueDate,
                                      boolean beforeSent, boolean dueSent, int daysToDue) {
        if (beforeSent && dueSent) {
            return "已提醒 " + BreedingCodes.monthDay(remindBeforeDate) + "、今天";
        }
        if (beforeSent) {
            if (remindBeforeDate != null && remindBeforeDate.equals(today())) {
                return "今天已发提前 " + Math.max(daysToDue, 0) + " 天提醒";
            }
            return "已提醒 " + BreedingCodes.monthDay(remindBeforeDate);
        }
        if (dueSent) {
            return "已提醒 今天";
        }
        return BreedingCodes.monthDay(remindBeforeDate) + "提醒";
    }
}
