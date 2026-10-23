package com.zhimu.slmp.finance.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.SaleOrderItemVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 模块10 显示格式（UI 设计文档 2.2“金额与数字”）：千分位、数字与单位之间不换行空格；支出前加“−”、收入前加“+”；
 * 环比与涨跌颜色按含义（{@link #TONE_GOOD} / {@link #TONE_BAD} / {@link #TONE_NEUTRAL}），不按方向。
 * 比例字段（*_rate）为 0–1 的小数。全部为纯 Java，供 ViewModel 与单元测试使用。
 */
public final class FinanceFormat {

    public static final String NBSP = NumberFormatter.NBSP;
    public static final String YUAN = "元";
    public static final String NO_DATA = "无数据";
    public static final String NOT_COMPARABLE = "无法比较";
    public static final String DASH = "—";

    /** 环比、涨跌的含义 */
    public static final int TONE_NONE = 0;
    public static final int TONE_GOOD = 1;
    public static final int TONE_BAD = 2;
    public static final int TONE_NEUTRAL = 3;

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);

    private FinanceFormat() {
    }

    // ===== 金额与数字 =====

    /** “87,600”“3,200.5”：整数不带小数，去掉末尾 0；null 为空串 */
    @NonNull
    public static String num(@Nullable BigDecimal v) {
        return NumberFormatter.formatNumber(v);
    }

    /** “87,600 元” */
    @NonNull
    public static String yuan(@Nullable BigDecimal v) {
        return v == null ? "" : num(v) + NBSP + YUAN;
    }

    /** 单价、价格两位小数“15.60” */
    @NonNull
    public static String price(@Nullable BigDecimal v) {
        return NumberFormatter.formatNumber(v, 2);
    }

    /** 收支记录金额：支出“−3,200 元”、收入“+6,000 元” */
    @NonNull
    public static String signedRecordAmount(@Nullable String recordType, @Nullable BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        String sign = FinanceConst.INCOME.equals(recordType) ? "+" : NumberFormatter.MINUS;
        return sign + num(amount.abs()) + NBSP + YUAN;
    }

    /** 盈亏数字：“+3,160”“−2,720”“0” */
    @NonNull
    public static String signed(@Nullable BigDecimal v) {
        if (v == null) {
            return DASH;
        }
        String s = num(v);
        return v.signum() > 0 ? "+" + s : s;
    }

    /** 比例“60.0%” */
    @NonNull
    public static String percent(@Nullable BigDecimal rate) {
        return NumberFormatter.formatPercent(rate);
    }

    /** 带符号比例“+32.1%”“−16.0%” */
    @NonNull
    public static String signedPercent(@Nullable BigDecimal rate) {
        return NumberFormatter.formatSignedPercent(rate);
    }

    /** 数量与计数单位“98 头” */
    @NonNull
    public static String count(int n, @Nullable String unitText) {
        return NumberFormatter.formatNumber(n) + NBSP + (unitText == null ? "" : unitText);
    }

    /** 重量“11,760 千克” */
    @NonNull
    public static String kg(@Nullable BigDecimal v) {
        return v == null ? "" : num(v) + NBSP + "千克";
    }

    // ===== 环比与涨跌 =====

    /** “环比 +11.1%”“环比持平”；不可比或比例为空时“无法比较” */
    @NonNull
    public static String mom(@NonNull String prefix, @Nullable BigDecimal rate, boolean comparable) {
        if (!comparable || rate == null) {
            return NOT_COMPARABLE;
        }
        if (rate.signum() == 0) {
            return prefix + "持平";
        }
        return prefix + " " + signedPercent(rate);
    }

    /**
     * 环比的含义：upIsGood 为真时上升有利（收入、结余、出栏价），为假时上升不利（支出、死亡率、种苗价）；
     * 不可比为 TONE_NONE，持平为 TONE_NEUTRAL。
     */
    public static int tone(@Nullable BigDecimal rate, boolean comparable, boolean upIsGood) {
        if (!comparable || rate == null) {
            return TONE_NONE;
        }
        if (rate.signum() == 0) {
            return TONE_NEUTRAL;
        }
        return (rate.signum() > 0) == upIsGood ? TONE_GOOD : TONE_BAD;
    }

    /** 方向：1 上升、-1 下降、0 持平或不可比 */
    public static int direction(@Nullable BigDecimal rate) {
        return rate == null ? 0 : rate.signum();
    }

    /** 行情涨跌：“涨 4.0%”“跌 2.0%”“持平” */
    @NonNull
    public static String priceChange(@Nullable BigDecimal rate, @Nullable String direction) {
        if (rate == null || FinanceConst.FLAT.equals(direction) || rate.signum() == 0) {
            return "持平";
        }
        boolean up = direction == null ? rate.signum() > 0 : FinanceConst.UP.equals(direction);
        return (up ? "涨 " : "跌 ") + percent(rate.abs());
    }

    /** 行情涨跌的含义：出栏价上涨有利，种苗价上涨不利 */
    public static int priceTone(@Nullable BigDecimal rate, @Nullable String direction, boolean riseIsGood) {
        if (rate == null || rate.signum() == 0 || FinanceConst.FLAT.equals(direction)) {
            return TONE_NEUTRAL;
        }
        boolean up = direction == null ? rate.signum() > 0 : FinanceConst.UP.equals(direction);
        return up == riseIsGood ? TONE_GOOD : TONE_BAD;
    }

    // ===== 月份 =====

    @NonNull
    public static String currentMonth() {
        return YearMonth.from(DateFormatter.today()).format(YM);
    }

    @NonNull
    public static String shiftMonth(@NonNull String ym, int delta) {
        return YearMonth.parse(ym, YM).plusMonths(delta).format(YM);
    }

    /** 本年“10月”，往年“2025年12月” */
    @NonNull
    public static String monthLabel(@NonNull String ym) {
        YearMonth m = YearMonth.parse(ym, YM);
        return m.getYear() == DateFormatter.today().getYear() ? m.getMonthValue() + "月"
                : m.getYear() + "年" + m.getMonthValue() + "月";
    }

    public static boolean isBeforeCurrentMonth(@NonNull String ym) {
        return YearMonth.parse(ym, YM).isBefore(YearMonth.from(DateFormatter.today()));
    }

    /** “yyyy-MM-dd”所在月份 */
    @Nullable
    public static String monthOf(@Nullable String date) {
        return date == null || date.length() < 7 ? null : date.substring(0, 7);
    }

    /** 日期“10月19日” */
    @NonNull
    public static String monthDay(@Nullable String date) {
        LocalDate d = DateFormatter.parseDate(date);
        return d == null ? "" : DateFormatter.monthDay(d);
    }

    /** 日期“10月19日 周一” */
    @NonNull
    public static String dateWeek(@Nullable String date) {
        return DateFormatter.formatDate(date);
    }

    // ===== 出栏单 =====

    /**
     * 约定收款状态标签（UI 2.2 E07）：已逾期“已逾期 4 天”；3 天内到期“今天到期”“明天到期”“3 天后到期”；更远或已收齐为 null。
     */
    @Nullable
    public static String dueTag(@NonNull SaleOrderItemVO o) {
        if (FinanceConst.DUE_OVERDUE.equals(o.getDueState())) {
            Integer d = o.getOverdueDays();
            return d == null ? (o.getDueStateText() == null ? "已逾期" : o.getDueStateText()) : "已逾期 " + d + " 天";
        }
        if (FinanceConst.DUE_SOON.equals(o.getDueState())) {
            Integer d = o.getDaysToDue();
            if (d == null) {
                return o.getDueStateText();
            }
            return d <= 0 ? "今天到期" : d == 1 ? "明天到期" : d + " 天后到期";
        }
        return null;
    }

    public static boolean isOverdue(@NonNull SaleOrderItemVO o) {
        return FinanceConst.DUE_OVERDUE.equals(o.getDueState());
    }

    public static boolean isVoided(@NonNull SaleOrderItemVO o) {
        return FinanceConst.PAY_VOIDED.equals(o.getPaymentStatus());
    }

    /** “98 头 · 11,760 千克” */
    @NonNull
    public static String quantityWeight(@NonNull SaleOrderItemVO o) {
        return count(o.getQuantity(), o.getCountUnitText()) + " · " + kg(o.getTotalWeight());
    }

    /** 收款进度 0–1000（LinearProgressIndicator 的 max） */
    public static int progress(@Nullable BigDecimal received, @Nullable BigDecimal total) {
        if (received == null || total == null || total.signum() <= 0) {
            return 0;
        }
        return received.multiply(BigDecimal.valueOf(1000)).divide(total, 0, java.math.RoundingMode.DOWN)
                .min(BigDecimal.valueOf(1000)).max(BigDecimal.ZERO).intValue();
    }
}
