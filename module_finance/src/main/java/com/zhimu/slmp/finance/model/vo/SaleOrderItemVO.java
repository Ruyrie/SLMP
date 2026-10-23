package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 出栏单卡片（API-E17、E18 列表项）
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class SaleOrderItemVO {

    private long id;
    private long batchId;
    private String batchNo;
    private String penName;
    private String buyerName;
    private String saleDate;
    private int quantity;
    private String countUnitText;
    private BigDecimal totalWeight;
    private BigDecimal saleAmount;
    private BigDecimal receivedAmount;
    private BigDecimal unpaidAmount;
    private BigDecimal progressRate;
    private String paymentStatusText;
    private boolean hasPendingChange;
    @Nullable
    private String effectiveDueDate;
    @Nullable
    private String dueStateText;
    private boolean voidByReview;
    private String createdAt;
    /** UNPAID / PARTIAL / PAID / VOIDED */
    private String paymentStatus;
    /** OVERDUE / DUE_SOON / DUE_LATER / SETTLED / VOIDED */
    private String dueState;
    @Nullable
    private String dueDate;
    @Nullable
    private Integer overdueDays;
    @Nullable
    private Integer daysToDue;
    @Nullable
    private String paidOffDate;
    @Nullable
    private String voidedAt;
    @Nullable
    private String voidReason;

    public long getId() {
        return id;
    }

    public long getBatchId() {
        return batchId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public String getPenName() {
        return penName;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public String getSaleDate() {
        return saleDate;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getCountUnitText() {
        return countUnitText;
    }

    public BigDecimal getTotalWeight() {
        return totalWeight;
    }

    public BigDecimal getSaleAmount() {
        return saleAmount;
    }

    public BigDecimal getReceivedAmount() {
        return receivedAmount;
    }

    public BigDecimal getUnpaidAmount() {
        return unpaidAmount;
    }

    public BigDecimal getProgressRate() {
        return progressRate;
    }

    public String getPaymentStatusText() {
        return paymentStatusText;
    }

    public boolean isHasPendingChange() {
        return hasPendingChange;
    }

    @Nullable
    public String getEffectiveDueDate() {
        return effectiveDueDate;
    }

    @Nullable
    public String getDueStateText() {
        return dueStateText;
    }

    public boolean isVoidByReview() {
        return voidByReview;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public String getDueState() {
        return dueState;
    }

    @Nullable
    public String getDueDate() {
        return dueDate;
    }

    @Nullable
    public Integer getOverdueDays() {
        return overdueDays;
    }

    @Nullable
    public Integer getDaysToDue() {
        return daysToDue;
    }

    @Nullable
    public String getPaidOffDate() {
        return paidOffDate;
    }

    @Nullable
    public String getVoidedAt() {
        return voidedAt;
    }

    @Nullable
    public String getVoidReason() {
        return voidReason;
    }
}
