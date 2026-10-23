package com.zhimu.slmp.finance.util;

import androidx.annotation.Nullable;

import com.zhimu.slmp.finance.model.FinanceConst;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * E06、E08 本地预估（技术准备文档 2.6.2）：均重一位小数；销售金额按重量 = 总重 × 单价、按头 = 数量 × 单价，HALF_UP 到元。
 * 只作预估，以服务端返回为准。
 */
public final class SaleAmountCalculator {

    private SaleAmountCalculator() {
    }

    /** 均重 = 总重 ÷ 数量（一位小数）；任一缺失或数量不大于 0 时为 null */
    @Nullable
    public static BigDecimal avgWeight(@Nullable BigDecimal totalWeight, @Nullable Integer quantity) {
        if (totalWeight == null || quantity == null || quantity <= 0 || totalWeight.signum() <= 0) {
            return null;
        }
        return totalWeight.divide(BigDecimal.valueOf(quantity), 1, RoundingMode.HALF_UP);
    }

    /** 销售金额（元）；计算所需字段未填完时为 null */
    @Nullable
    public static BigDecimal saleAmount(@Nullable String pricingMethod, @Nullable Integer quantity,
                                        @Nullable BigDecimal totalWeight, @Nullable BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() <= 0) {
            return null;
        }
        BigDecimal base;
        if (FinanceConst.BY_HEAD.equals(pricingMethod)) {
            if (quantity == null || quantity <= 0) {
                return null;
            }
            base = BigDecimal.valueOf(quantity);
        } else {
            if (totalWeight == null || totalWeight.signum() <= 0) {
                return null;
            }
            base = totalWeight;
        }
        return base.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP);
    }

    /** 提交后剩余存栏；数量未填时为 null */
    @Nullable
    public static Integer remaining(int maxQuantity, @Nullable Integer quantity) {
        if (quantity == null || quantity <= 0) {
            return null;
        }
        return Math.max(maxQuantity - quantity, 0);
    }

    /** 解析整数输入；空或格式不对为 null */
    @Nullable
    public static Integer parseInt(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }
        String s = text.toString().replace(",", "").trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析金额、重量输入（去掉千分位）；空或格式不对为 null */
    @Nullable
    public static BigDecimal parseDecimal(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }
        String s = text.toString().replace(",", "").trim();
        if (s.isEmpty() || ".".equals(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
