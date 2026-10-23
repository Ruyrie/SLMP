package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-E19–E22 出栏单详情
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class SaleOrderDetailVO extends SaleOrderItemVO {

    private String saleNo;
    private String categoryName;
    @Nullable
    private String breedName;
    /** BY_WEIGHT / BY_HEAD */
    private String pricingMethod;
    private String pricingMethodText;
    private BigDecimal unitPrice;
    @Nullable
    private BigDecimal avgWeight;
    private String batchStatus;
    @Nullable
    private UserBriefVO registrant;
    private String registeredAt;
    private int contentVersion;
    private int version;
    @Nullable
    private BigDecimal refUnitPrice;
    private List<PenAllocation> penAllocations;
    private List<Receipt> receipts;
    @Nullable
    private PendingChange pendingChange;
    private Actions actions;
    @Nullable
    private SettlementBrief settlementBrief;
    /** 只在 E22 */
    @Nullable
    private ReceiptResult receiptResult;

    public String getSaleNo() {
        return saleNo;
    }

    public String getCategoryName() {
        return categoryName;
    }

    @Nullable
    public String getBreedName() {
        return breedName;
    }

    public String getPricingMethod() {
        return pricingMethod;
    }

    public String getPricingMethodText() {
        return pricingMethodText;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    @Nullable
    public BigDecimal getAvgWeight() {
        return avgWeight;
    }

    public String getBatchStatus() {
        return batchStatus;
    }

    @Nullable
    public UserBriefVO getRegistrant() {
        return registrant;
    }

    public String getRegisteredAt() {
        return registeredAt;
    }

    public int getContentVersion() {
        return contentVersion;
    }

    public int getVersion() {
        return version;
    }

    @Nullable
    public BigDecimal getRefUnitPrice() {
        return refUnitPrice;
    }

    @NonNull
    public List<PenAllocation> getPenAllocations() {
        return penAllocations == null ? java.util.Collections.emptyList() : penAllocations;
    }

    @NonNull
    public List<Receipt> getReceipts() {
        return receipts == null ? java.util.Collections.emptyList() : receipts;
    }

    @Nullable
    public PendingChange getPendingChange() {
        return pendingChange;
    }

    public Actions getActions() {
        return actions;
    }

    @Nullable
    public SettlementBrief getSettlementBrief() {
        return settlementBrief;
    }

    @Nullable
    public ReceiptResult getReceiptResult() {
        return receiptResult;
    }

    /** 栏舍分配 */
    public static class PenAllocation {

        private long penId;
        private String penName;
        private int quantity;

        public long getPenId() {
            return penId;
        }

        public String getPenName() {
            return penName;
        }

        public int getQuantity() {
            return quantity;
        }
    }

    /** 收款记录 */
    public static class Receipt {

        private long id;
        private BigDecimal amount;
        private String receiptDate;
        private String paymentMethod;
        private String paymentMethodText;
        private String operatorName;
        @Nullable
        private Long financeRecordId;
        private String status;

        public long getId() {
            return id;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getReceiptDate() {
            return receiptDate;
        }

        public String getPaymentMethod() {
            return paymentMethod;
        }

        public String getPaymentMethodText() {
            return paymentMethodText;
        }

        public String getOperatorName() {
            return operatorName;
        }

        @Nullable
        public Long getFinanceRecordId() {
            return financeRecordId;
        }

        public String getStatus() {
            return status;
        }
    }

    /** 待审核的修改申请 */
    public static class PendingChange {

        @Nullable
        private Long changeRequestId;
        private String changeType;
        @Nullable
        private String fieldCode;
        private String summaryText;

        @Nullable
        public Long getChangeRequestId() {
            return changeRequestId;
        }

        public String getChangeType() {
            return changeType;
        }

        @Nullable
        public String getFieldCode() {
            return fieldCode;
        }

        public String getSummaryText() {
            return summaryText;
        }
    }

    /** 可用操作 */
    public static class Actions {

        private boolean canReceive;
        private boolean canEdit;
        private boolean canVoid;
        private boolean canApplyChange;
        private boolean changeInReview;

        public boolean isCanReceive() {
            return canReceive;
        }

        public boolean isCanEdit() {
            return canEdit;
        }

        public boolean isCanVoid() {
            return canVoid;
        }

        public boolean isCanApplyChange() {
            return canApplyChange;
        }

        public boolean isChangeInReview() {
            return changeInReview;
        }
    }

    /** 批次核算摘要 */
    public static class SettlementBrief {

        private String status;
        @Nullable
        private BigDecimal profit;
        @Nullable
        private BigDecimal saleIncome;
        @Nullable
        private BigDecimal costTotal;
        @Nullable
        private BigDecimal costPerHead;
        @Nullable
        private BigDecimal fcr;
        private int pendingSupplementCount;

        public String getStatus() {
            return status;
        }

        @Nullable
        public BigDecimal getProfit() {
            return profit;
        }

        @Nullable
        public BigDecimal getSaleIncome() {
            return saleIncome;
        }

        @Nullable
        public BigDecimal getCostTotal() {
            return costTotal;
        }

        @Nullable
        public BigDecimal getCostPerHead() {
            return costPerHead;
        }

        @Nullable
        public BigDecimal getFcr() {
            return fcr;
        }

        public int getPendingSupplementCount() {
            return pendingSupplementCount;
        }
    }

    /** 本次收款结果（E22） */
    public static class ReceiptResult {

        private BigDecimal amount;
        private BigDecimal unpaidAmount;
        private boolean paidOff;

        public BigDecimal getAmount() {
            return amount;
        }

        public BigDecimal getUnpaidAmount() {
            return unpaidAmount;
        }

        public boolean isPaidOff() {
            return paidOff;
        }
    }
}
