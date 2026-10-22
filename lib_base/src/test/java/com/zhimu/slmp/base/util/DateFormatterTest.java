package com.zhimu.slmp.base.util;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.LocalDateTime;

/** 日期显示规则（02 号规范 9.3 节），“今天”固定为 2026-10-19 周一 */
public class DateFormatterTest {

    @Before
    public void fixToday() {
        DateFormatter.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 19, 9, 30)
                .atZone(DateFormatter.ZONE).toInstant(), DateFormatter.ZONE));
    }

    @Test
    public void todayDateTime() {
        assertEquals("今天 14:20", DateFormatter.formatDateTime("2026-10-19 14:20:05"));
    }

    @Test
    public void sameYearDateTime() {
        assertEquals("10月18日 09:28", DateFormatter.formatDateTime("2026-10-18 09:28:00"));
    }

    @Test
    public void dateWithWeek() {
        assertEquals("10月19日 周一", DateFormatter.formatDate("2026-10-19"));
        assertEquals("10月29日 周四", DateFormatter.formatDate("2026-10-29"));
        assertEquals("10月5日", DateFormatter.formatDate("2026-10-05", false));
    }

    @Test
    public void crossYearDate() {
        assertEquals("2027年3月15日", DateFormatter.formatDate("2027-03-15"));
    }

    @Test
    public void monthRangeAndDuration() {
        assertEquals("2026年10月", DateFormatter.formatMonth("2026-10"));
        assertEquals("10月5日–10月9日", DateFormatter.formatDateRange("2026-10-05", "2026-10-09"));
        assertEquals("3:42", DateFormatter.formatDuration(222));
        assertEquals("0:18", DateFormatter.formatDuration(18));
    }

    @Test
    public void invalidInputReturnedAsIs() {
        assertEquals("", DateFormatter.formatDate(null));
        assertEquals("明天", DateFormatter.formatDate("明天"));
    }
}
