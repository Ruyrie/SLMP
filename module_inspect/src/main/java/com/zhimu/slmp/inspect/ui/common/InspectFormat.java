package com.zhimu.slmp.inspect.ui.common;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * 本模块的显示格式：距离“120 米”“3.2 千米”、完成率“完成 50.0%”、发病率“6.0”、日期“10月18日 周日”。
 */
public final class InspectFormat {

    public static final String DOT = " · ";

    private InspectFormat() {
    }

    /** 距离：1000 米以内写“120 米”，以上写“3.2 千米” */
    @NonNull
    public static String distance(int meters) {
        if (meters < 1000) {
            return meters + " 米";
        }
        BigDecimal km = BigDecimal.valueOf(meters).divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
        return km.stripTrailingZeros().toPlainString() + " 千米";
    }

    /** “完成 50.0%”（保留一位小数）；比例为空时“完成 0.0%” */
    @NonNull
    public static String completion(@Nullable BigDecimal ratio) {
        BigDecimal r = ratio == null ? BigDecimal.ZERO : ratio;
        return "完成 " + r.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** 百分比数值“6.0”（保留一位小数） */
    @NonNull
    public static String percent1(@Nullable BigDecimal ratio) {
        BigDecimal r = ratio == null ? BigDecimal.ZERO : ratio;
        return r.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    /** “10月18日 周日” */
    @NonNull
    public static String dateWithWeek(@Nullable String date) {
        return DateFormatter.formatDate(date, true);
    }

    /** “10月18日” */
    @NonNull
    public static String monthDay(@Nullable String date) {
        LocalDate d = DateFormatter.parseDate(date);
        return d == null ? (date == null ? "" : date) : DateFormatter.monthDay(d);
    }

    /** “09:12” */
    @NonNull
    public static String time(@Nullable String datetime) {
        return DateFormatter.formatTime(datetime);
    }

    /** “HH:mm” 减 60 分钟（提醒时刻） */
    @NonNull
    public static String minusHour(@NonNull String hhmm) {
        int[] t = parseTime(hhmm);
        if (t == null) {
            return hhmm;
        }
        int m = t[0] * 60 + t[1] - 60;
        if (m < 0) {
            m += 24 * 60;
        }
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    /** “HH:mm” → {时, 分}；格式不对返回 null */
    @Nullable
    public static int[] parseTime(@Nullable String hhmm) {
        if (hhmm == null || !hhmm.matches("\\d{1,2}:\\d{2}")) {
            return null;
        }
        String[] p = hhmm.split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        if (h > 23 || m > 59) {
            return null;
        }
        return new int[]{h, m};
    }

    @NonNull
    public static String hhmm(int hour, int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    /** 用“、”连接；为空时返回 fallback */
    @NonNull
    public static String join(@Nullable List<String> list, @NonNull String fallback) {
        return list == null || list.isEmpty() ? fallback : TextUtils.join("、", list);
    }

    /** “0:18” */
    @NonNull
    public static String duration(int seconds) {
        int s = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
