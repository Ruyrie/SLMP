package com.zhimu.slmp.feed.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.NumberFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 本模块的文字格式：重量“1,050 千克”、平均体重 1 位小数“60.0 千克”、百分比 1 位小数（负号为减号）、
 * 逾期时长“超过投喂时间 2 小时 30 分”。饲料单价与金额任何角色都不显示，这里不提供金额格式。
 */
public final class FeedFormat {

    public static final String DOT = " · ";
    public static final String KG = "千克";
    public static final String HEAD = "头";
    public static final String DASH = "—";
    public static final String NO_DATA = "无数据";

    private FeedFormat() {
    }

    /** “1,050 千克”“2.4 千克”（去掉末尾的 0） */
    @NonNull
    public static String kg(@Nullable BigDecimal value) {
        return value == null ? "" : NumberFormatter.formatQuantity(value, KG);
    }

    /** 数字部分（千分位、去掉末尾的 0）：“1,050” */
    @NonNull
    public static String num(@Nullable BigDecimal value) {
        return value == null ? "" : NumberFormatter.formatNumber(value);
    }

    /** 1 位小数：“60.0” */
    @NonNull
    public static String num1(@Nullable BigDecimal value) {
        return value == null ? "" : NumberFormatter.formatNumber(value, 1);
    }

    /** 2 位小数：“0.81” */
    @NonNull
    public static String num2(@Nullable BigDecimal value) {
        return value == null ? "" : NumberFormatter.formatNumber(value, 2);
    }

    /** “60.0 千克” */
    @NonNull
    public static String kg1(@Nullable BigDecimal value) {
        return value == null ? "" : NumberFormatter.formatQuantity(value, 1, KG);
    }

    /** “0.81 千克” */
    @NonNull
    public static String kg2(@Nullable BigDecimal value) {
        return value == null ? "" : NumberFormatter.formatQuantity(value, 2, KG);
    }

    /** “200 头” */
    @NonNull
    public static String head(@Nullable Integer count) {
        return count == null ? "" : NumberFormatter.formatQuantity(count, HEAD);
    }

    /** 比例的百分数绝对值，1 位小数：“12.5%” */
    @NonNull
    public static String absPercent(@Nullable BigDecimal ratio) {
        return ratio == null ? "" : NumberFormatter.formatPercent(ratio.abs());
    }

    /** 带正负号：“−12.5%”“+15.0%” */
    @NonNull
    public static String signedPercent(@Nullable BigDecimal ratio) {
        return ratio == null ? "" : NumberFormatter.formatSignedPercent(ratio);
    }

    /** 指标卡数字部分（不带 %）：“−12.5”“+11.2”“95.2” */
    @NonNull
    public static String percentNumber(@Nullable BigDecimal ratio, boolean signed) {
        if (ratio == null) {
            return "";
        }
        BigDecimal pct = ratio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
        String s = NumberFormatter.formatNumber(pct, 1);
        return signed && pct.signum() > 0 ? "+" + s : s;
    }

    /** 阈值比例转为百分数（不带 %）：0.1000 → “10” */
    @NonNull
    public static String thresholdPercent(@Nullable BigDecimal ratio) {
        if (ratio == null) {
            return "";
        }
        return NumberFormatter.formatNumber(ratio.multiply(BigDecimal.valueOf(100)));
    }

    /** “超过投喂时间 2 小时 30 分” */
    @NonNull
    public static String overdue(@Nullable Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return "";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        StringBuilder sb = new StringBuilder("超过投喂时间 ");
        if (h > 0) {
            sb.append(h).append(" 小时");
            if (m > 0) {
                sb.append(' ');
            }
        }
        if (m > 0) {
            sb.append(m).append(" 分");
        }
        return sb.toString();
    }

    /** “10月19日”（跨年带年份） */
    @NonNull
    public static String date(@Nullable String date) {
        return DateFormatter.formatDate(date, false);
    }

    /** “10月19日 周一” */
    @NonNull
    public static String dateWithWeek(@Nullable String date) {
        return DateFormatter.formatDate(date, true);
    }

    /** “16日” */
    @NonNull
    public static String dayOfMonth(@Nullable String date) {
        java.time.LocalDate d = DateFormatter.parseDate(date);
        return d == null ? "" : d.getDayOfMonth() + "日";
    }

    @NonNull
    public static String orEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }

    /** 用“ · ”连接非空部分 */
    @NonNull
    public static String join(@Nullable String... parts) {
        StringBuilder sb = new StringBuilder();
        if (parts == null) {
            return "";
        }
        for (String p : parts) {
            if (p == null || p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(DOT);
            }
            sb.append(p);
        }
        return sb.toString();
    }
}
