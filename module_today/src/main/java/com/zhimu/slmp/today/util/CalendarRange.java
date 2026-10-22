package com.zhimu.slmp.today.util;

import androidx.annotation.NonNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * 日历的日期范围与周期文字（不依赖手机时间，today 由调用方给出）。
 * <ul>
 * <li>月视图：含本月的完整周，周一开始，5 或 6 行（不超过 42 天），例如 2026年10月为 9月28日–11月1日；</li>
 * <li>周、日视图：所在周的周一到周日（API-T03 只取这 7 天）。</li>
 * </ul>
 */
public final class CalendarRange {

    private static final String[] WEEK = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    @NonNull
    private final LocalDate start;
    @NonNull
    private final LocalDate end;

    private CalendarRange(@NonNull LocalDate start, @NonNull LocalDate end) {
        this.start = start;
        this.end = end;
    }

    /** 视图对应的请求范围 */
    @NonNull
    public static CalendarRange of(@NonNull CalendarMode mode, @NonNull LocalDate selected) {
        if (mode == CalendarMode.MONTH) {
            return month(YearMonth.from(selected));
        }
        return week(selected);
    }

    @NonNull
    public static CalendarRange month(@NonNull YearMonth ym) {
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
        return new CalendarRange(weekStart(first), last.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
    }

    @NonNull
    public static CalendarRange week(@NonNull LocalDate any) {
        LocalDate s = weekStart(any);
        return new CalendarRange(s, s.plusDays(6));
    }

    /** 所在周的周一 */
    @NonNull
    public static LocalDate weekStart(@NonNull LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** 月视图行数（5 或 6；2 月从周一开始时可能为 4） */
    public static int rowsOf(@NonNull YearMonth ym) {
        return (int) (month(ym).days() / 7);
    }

    @NonNull
    public LocalDate getStart() {
        return start;
    }

    @NonNull
    public LocalDate getEnd() {
        return end;
    }

    /** 含两端的天数 */
    public long days() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** 上一个 / 下一个周期（月视图翻月、周视图翻周、日视图翻天），返回新的选中日期 */
    @NonNull
    public static LocalDate shift(@NonNull CalendarMode mode, @NonNull LocalDate selected, int delta) {
        switch (mode) {
            case MONTH:
                // 翻月后选中该月 1 日（同月的“今天”除外，由调用方处理）
                return YearMonth.from(selected).plusMonths(delta).atDay(1);
            case WEEK:
                return selected.plusWeeks(delta);
            case DAY:
            default:
                return selected.plusDays(delta);
        }
    }

    /** 周期文字：“2026年10月”“10月19日–25日”“10月19日 周一” */
    @NonNull
    public static String periodText(@NonNull CalendarMode mode, @NonNull LocalDate selected) {
        switch (mode) {
            case MONTH:
                return selected.getYear() + "年" + selected.getMonthValue() + "月";
            case WEEK: {
                CalendarRange w = week(selected);
                LocalDate s = w.start;
                LocalDate e = w.end;
                String left = s.getMonthValue() + "月" + s.getDayOfMonth() + "日";
                String right = s.getMonthValue() == e.getMonthValue()
                        ? e.getDayOfMonth() + "日"
                        : e.getMonthValue() + "月" + e.getDayOfMonth() + "日";
                if (s.getYear() != e.getYear()) {
                    left = s.getYear() + "年" + left;
                    right = e.getYear() + "年" + e.getMonthValue() + "月" + e.getDayOfMonth() + "日";
                }
                return left + "–" + right;
            }
            case DAY:
            default:
                return dayText(selected);
        }
    }

    /** “10月19日 周一” */
    @NonNull
    public static String dayText(@NonNull LocalDate d) {
        return d.getMonthValue() + "月" + d.getDayOfMonth() + "日 " + WEEK[d.getDayOfWeek().getValue() - 1];
    }

    /** 分组标题：“10月19日 周一 · 今天” */
    @NonNull
    public static String dayHeader(@NonNull LocalDate d, boolean isToday) {
        return isToday ? dayText(d) + " · 今天" : dayText(d);
    }
}
