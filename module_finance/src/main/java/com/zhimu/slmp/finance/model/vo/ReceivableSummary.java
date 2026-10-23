package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 应收合计（E01 待收款卡、E09 合计卡）
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class ReceivableSummary {

    private BigDecimal totalUnpaidAmount;
    private int orderCount;
    private int overdueCount;
    @Nullable
    private BigDecimal overdueUnpaidAmount;
    @Nullable
    private String nearestDueDate;

    public BigDecimal getTotalUnpaidAmount() {
        return totalUnpaidAmount;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public int getOverdueCount() {
        return overdueCount;
    }

    @Nullable
    public BigDecimal getOverdueUnpaidAmount() {
        return overdueUnpaidAmount;
    }

    @Nullable
    public String getNearestDueDate() {
        return nearestDueDate;
    }
}
