package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * API-E23 修改申请预计结果
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class SaleChangePreviewVO {

    private BigDecimal saleAmountBefore;
    private BigDecimal unpaidAmountBefore;
    private BigDecimal receivedAmount;
    @Nullable
    private BigDecimal profitBefore;
    @Nullable
    private BigDecimal saleAmountAfter;
    @Nullable
    private BigDecimal unpaidAmountAfter;
    @Nullable
    private BigDecimal profitAfter;
    @Nullable
    private Boolean turnToLoss;
    @Nullable
    private Integer restoreCount;
    @Nullable
    private Integer voidReceiptCount;
    @Nullable
    private BigDecimal voidReceiptAmount;

    public BigDecimal getSaleAmountBefore() {
        return saleAmountBefore;
    }

    public BigDecimal getUnpaidAmountBefore() {
        return unpaidAmountBefore;
    }

    public BigDecimal getReceivedAmount() {
        return receivedAmount;
    }

    @Nullable
    public BigDecimal getProfitBefore() {
        return profitBefore;
    }

    @Nullable
    public BigDecimal getSaleAmountAfter() {
        return saleAmountAfter;
    }

    @Nullable
    public BigDecimal getUnpaidAmountAfter() {
        return unpaidAmountAfter;
    }

    @Nullable
    public BigDecimal getProfitAfter() {
        return profitAfter;
    }

    @Nullable
    public Boolean getTurnToLoss() {
        return turnToLoss;
    }

    @Nullable
    public Integer getRestoreCount() {
        return restoreCount;
    }

    @Nullable
    public Integer getVoidReceiptCount() {
        return voidReceiptCount;
    }

    @Nullable
    public BigDecimal getVoidReceiptAmount() {
        return voidReceiptAmount;
    }
}
