package com.zhimu.slmp.feed.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 投喂偏差与异常（与服务端 FeedCalc 一致）：偏差 =（实投 − 计划）÷ 计划，保留 4 位；
 * 偏差绝对值大于阈值才算投喂异常（等于阈值不算），少投为 UNDER、多投为 OVER。
 */
public final class FeedCalc {

    public static final String NONE = "NONE";
    public static final String UNDER = "UNDER";
    public static final String OVER = "OVER";

    /** 实际投喂量上限 99999.9 千克 */
    public static final BigDecimal MAX_ACTUAL = new BigDecimal("99999.9");

    public static final String ERR_EMPTY = "请填写实际投喂量";
    public static final String ERR_NOT_POSITIVE = "实际投喂量要大于 0 千克";
    public static final String ERR_SCALE = "实际投喂量最多 1 位小数";
    public static final String ERR_TOO_LARGE = "实际投喂量不能超过 99,999.9 千克";

    private FeedCalc() {
    }

    /** 输入文本转数值（允许千分位逗号）；不是数字时返回 null */
    @Nullable
    public static BigDecimal parse(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }
        String s = text.toString().trim().replace(",", "");
        if (s.isEmpty() || ".".equals(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 偏差比例；计划为空或为 0、实投为空时返回 null */
    @Nullable
    public static BigDecimal deviation(@Nullable BigDecimal plan, @Nullable BigDecimal actual) {
        if (plan == null || actual == null || plan.signum() == 0) {
            return null;
        }
        return actual.subtract(plan).divide(plan, 4, RoundingMode.HALF_UP);
    }

    /** 投喂异常类型：|偏差| > 阈值时少投 UNDER、多投 OVER，否则 NONE */
    @NonNull
    public static String abnormalType(@Nullable BigDecimal deviation, @Nullable BigDecimal threshold) {
        if (deviation == null || threshold == null) {
            return NONE;
        }
        if (deviation.abs().compareTo(threshold) <= 0) {
            return NONE;
        }
        return deviation.signum() < 0 ? UNDER : OVER;
    }

    public static boolean isAbnormal(@Nullable BigDecimal deviation, @Nullable BigDecimal threshold) {
        return !NONE.equals(abnormalType(deviation, threshold));
    }

    /**
     * 投喂异常横幅正文（不含粗体“投喂异常：”）：“实际比计划少 12.5%，超过 10%。确认后这次投喂会标为投喂异常。”；
     * 不超过阈值时返回 null。
     */
    @Nullable
    public static String abnormalText(@Nullable BigDecimal deviation, @Nullable BigDecimal threshold) {
        String type = abnormalType(deviation, threshold);
        if (NONE.equals(type)) {
            return null;
        }
        return "实际比计划" + (UNDER.equals(type) ? "少 " : "多 ") + FeedFormat.absPercent(deviation)
                + "，超过 " + FeedFormat.thresholdPercent(threshold) + "%。确认后这次投喂会标为投喂异常。";
    }

    /** 计划量来源：“200 头 × 每头日投喂 2.4 千克 ÷ 3 餐”，有偏差时后接“ · 偏差 −12.5%” */
    @NonNull
    public static String planSource(@Nullable Integer headCount, @Nullable BigDecimal dailyQty,
                                    @Nullable Integer meals, @Nullable BigDecimal deviation) {
        StringBuilder sb = new StringBuilder();
        if (headCount != null && dailyQty != null && meals != null) {
            sb.append(FeedFormat.head(headCount)).append(" × 每头日投喂 ").append(FeedFormat.kg(dailyQty))
                    .append(" ÷ ").append(meals).append(" 餐");
        }
        if (deviation != null) {
            if (sb.length() > 0) {
                sb.append(FeedFormat.DOT);
            }
            sb.append("偏差 ").append(FeedFormat.signedPercent(deviation));
        }
        return sb.toString();
    }

    /** 实际投喂量格式校验（不含结存）：返回错误文案，通过时为 null */
    @Nullable
    public static String checkActual(@Nullable CharSequence text) {
        String s = text == null ? "" : text.toString().trim();
        if (s.isEmpty()) {
            return ERR_EMPTY;
        }
        BigDecimal v = parse(s);
        if (v == null || v.signum() <= 0) {
            return ERR_NOT_POSITIVE;
        }
        if (v.stripTrailingZeros().scale() > 1) {
            return ERR_SCALE;
        }
        if (v.compareTo(MAX_ACTUAL) > 0) {
            return ERR_TOO_LARGE;
        }
        return null;
    }

    /**
     * 本次需要从结存中出库的数量：新增或换饲料为实投；修改同一饲料为“新实投 − 原实投”（可为负，表示退回）。
     *
     * @param originalQty 修改模式同一饲料时的原实投，其余为 null
     */
    @Nullable
    public static BigDecimal outboundNeed(@Nullable BigDecimal actual, @Nullable BigDecimal originalQty) {
        if (actual == null) {
            return null;
        }
        return originalQty == null ? actual : actual.subtract(originalQty);
    }

    /** 是否超过结存（结存未知时不拦截） */
    public static boolean exceedsStock(@Nullable BigDecimal actual, @Nullable BigDecimal stock,
                                       @Nullable BigDecimal originalQty) {
        BigDecimal need = outboundNeed(actual, originalQty);
        return need != null && stock != null && need.compareTo(stock) > 0;
    }
}
