package com.zhimu.slmp.stock.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.NumberFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 本地估算（只用于显示，提交后以服务端为准）：入库金额、按均价折合、盘盈盘亏、报废损失、退货金额；
 * 金额一律 BigDecimal、两位小数、四舍五入。输入框原文的解析也在这里。
 */
public final class StockCalc {

    private StockCalc() {
    }

    /** 输入框原文 → BigDecimal：去掉千分位逗号与空格；空或非法返回 null */
    @Nullable
    public static BigDecimal parse(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }
        String s = text.toString().replace(",", "").replace("，", "").replace(" ", "")
                .replace(NumberFormatter.NBSP, "").trim();
        if (s.isEmpty() || ".".equals(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 小数位数（去掉末尾 0 后） */
    public static int scaleOf(@NonNull BigDecimal v) {
        return Math.max(v.stripTrailingZeros().scale(), 0);
    }

    public static boolean isInteger(@NonNull BigDecimal v) {
        return v.signum() == 0 || v.stripTrailingZeros().scale() <= 0;
    }

    /** 金额 = 数量 × 单价（两位小数）；任一为空返回 null */
    @Nullable
    public static BigDecimal amount(@Nullable BigDecimal qty, @Nullable BigDecimal price) {
        if (qty == null || price == null) {
            return null;
        }
        return qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    /** “3,200.00”（不带单位）；null 返回“—” */
    @NonNull
    public static String money(@Nullable BigDecimal amount) {
        return amount == null ? "—" : NumberFormatter.formatAmount(amount, null);
    }

    /** 汇总金额：整数不带小数“840 元”，否则“840.50 元” */
    @NonNull
    public static String moneySummary(@Nullable BigDecimal amount) {
        return amount == null ? "—" : NumberFormatter.formatAmountSummary(amount);
    }

    /** 回填输入框的数量原文：去掉末尾 0，不加千分位 */
    @NonNull
    public static String plain(@Nullable BigDecimal v) {
        if (v == null) {
            return "";
        }
        BigDecimal s = v.stripTrailingZeros();
        if (s.scale() < 0) {
            s = s.setScale(0);
        }
        return s.toPlainString();
    }

    /** 包装数 → 记账数量 */
    @NonNull
    public static BigDecimal toMeasure(@NonNull BigDecimal packQty, @Nullable BigDecimal packSize) {
        return packSize == null || packSize.signum() <= 0 ? packQty : packQty.multiply(packSize);
    }

    /** 记账数量 → 包装数（最多两位小数，向下取整避免超过结存） */
    @NonNull
    public static BigDecimal toPack(@NonNull BigDecimal measure, @Nullable BigDecimal packSize) {
        if (packSize == null || packSize.signum() <= 0) {
            return measure;
        }
        return measure.divide(packSize, 2, RoundingMode.DOWN).stripTrailingZeros();
    }

    /** 两个数是否相等（忽略小数位） */
    public static boolean same(@Nullable BigDecimal a, @Nullable BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    @NonNull
    public static BigDecimal orZero(@Nullable BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
