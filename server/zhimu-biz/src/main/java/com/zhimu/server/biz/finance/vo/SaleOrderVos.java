package com.zhimu.server.biz.finance.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 出栏与收款（API-E14–E23）响应结构，字段与 App model/vo 一致。 */
public final class SaleOrderVos {

    private SaleOrderVos() {
    }

    @Data
    public static class ItemVO {
        private Long id;
        private Long batchId;
        private String batchNo;
        private String penName;
        private String buyerName;
        private String saleDate;
        private Integer quantity;
        private String countUnitText;
        @ManagerView
        private BigDecimal totalWeight;
        @ManagerView
        private BigDecimal saleAmount;
        @ManagerView
        private BigDecimal receivedAmount;
        @ManagerView
        private BigDecimal unpaidAmount;
        private BigDecimal progressRate;
        private String paymentStatusText;
        private Boolean hasPendingChange;
        private String effectiveDueDate;
        private String dueStateText;
        private Boolean voidByReview;
        private String createdAt;
        private String paymentStatus;
        private String dueState;
        private String dueDate;
        private Integer overdueDays;
        private Integer daysToDue;
        private String paidOffDate;
        private String voidedAt;
        private String voidReason;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DetailVO extends ItemVO {
        private String saleNo;
        private String categoryName;
        private String breedName;
        private String pricingMethod;
        private String pricingMethodText;
        @ManagerView
        private BigDecimal unitPrice;
        @ManagerView
        private BigDecimal avgWeight;
        private String batchStatus;
        private UserBriefVO registrant;
        private String registeredAt;
        private Integer contentVersion;
        private Integer version;
        @ManagerView
        private BigDecimal refUnitPrice;
        private List<PenAllocation> penAllocations = new ArrayList<>();
        private List<Receipt> receipts = new ArrayList<>();
        private PendingChange pendingChange;
        private Actions actions = new Actions();
        private SettlementBrief settlementBrief;
        private ReceiptResult receiptResult;
    }

    /** 与 t_sale_order.pen_allocations 的 JSON 键一致（pen_id / pen_name / quantity） */
    @Data
    public static class PenAllocation {
        @com.fasterxml.jackson.annotation.JsonProperty("pen_id")
        private Long penId;
        @com.fasterxml.jackson.annotation.JsonProperty("pen_name")
        private String penName;
        @com.fasterxml.jackson.annotation.JsonProperty("quantity")
        private Integer quantity;
    }

    @Data
    public static class Receipt {
        private Long id;
        @ManagerView
        private BigDecimal amount;
        private String receiptDate;
        private String paymentMethod;
        private String paymentMethodText;
        private String operatorName;
        private Long financeRecordId;
        private String status;
    }

    @Data
    public static class PendingChange {
        private Long changeRequestId;
        private String changeType;
        private String fieldCode;
        private String summaryText;
    }

    @Data
    public static class Actions {
        private Boolean canReceive;
        private Boolean canEdit;
        private Boolean canVoid;
        private Boolean canApplyChange;
        private Boolean changeInReview;
    }

    @Data
    public static class SettlementBrief {
        private String status;
        @ManagerView
        private BigDecimal profit;
        @ManagerView
        private BigDecimal saleIncome;
        @ManagerView
        private BigDecimal costTotal;
        @ManagerView
        private BigDecimal costPerHead;
        private BigDecimal fcr;
        private Integer pendingSupplementCount;
    }

    @Data
    public static class ReceiptResult {
        @ManagerView
        private BigDecimal amount;
        @ManagerView
        private BigDecimal unpaidAmount;
        private Boolean paidOff;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class OrderPageVO extends PageVO<ItemVO> {
        private List<MonthGroup> monthGroups = new ArrayList<>();
    }

    @Data
    public static class MonthGroup {
        private String month;
        private String label;
        private Integer count;
        @ManagerView
        private BigDecimal saleAmount;
    }

    @Data
    public static class CreatedVO {
        private Long id;
        private String batchNo;
        private Integer remainingCount;
        private String countUnitText;
        private Boolean batchEnded;
    }

    @Data
    public static class BatchOptionVO {
        private Long batchId;
        private String batchNo;
        private String penName;
        private Long categoryId;
        private String categoryName;
        private String breedName;
        private String countUnit;
        private String countUnitText;
        private Integer dayAge;
        private Integer currentCount;
        private String entryDate;
        private Boolean inWithdrawal;
        private List<PenStock> pens = new ArrayList<>();
        private String sellableDate;
        private Integer withdrawalDaysLeft;
        private String withdrawalDrugName;
    }

    @Data
    public static class PenStock {
        private Long penId;
        private String penName;
        private Integer stockCount;
    }

    @Data
    public static class PrefillVO {
        private MarketPrice marketPrice;
        private List<String> recentBuyers = new ArrayList<>();
        private String lastBuyer;
        private String lastPaymentMethod;
    }

    @Data
    public static class MarketPrice {
        private String provinceName;
        private String categoryName;
        @ManagerView
        private BigDecimal price;
        private String priceUnitText;
        private String priceDate;
    }

    @Data
    public static class ChangePreviewVO {
        @ManagerView
        private BigDecimal saleAmountBefore;
        @ManagerView
        private BigDecimal unpaidAmountBefore;
        @ManagerView
        private BigDecimal receivedAmount;
        @ManagerView
        private BigDecimal profitBefore;
        @ManagerView
        private BigDecimal saleAmountAfter;
        @ManagerView
        private BigDecimal unpaidAmountAfter;
        @ManagerView
        private BigDecimal profitAfter;
        private Boolean turnToLoss;
        private Integer restoreCount;
        private Integer voidReceiptCount;
        @ManagerView
        private BigDecimal voidReceiptAmount;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ReceivableVO extends PageVO<ItemVO> {
        private String reminderReceivers;
        private String remindRuleText;
        private ReceivableSummary summary;
    }

    @Data
    public static class ReceivableSummary {
        @ManagerView
        private BigDecimal totalUnpaidAmount;
        private Integer orderCount;
        private Integer overdueCount;
        @ManagerView
        private BigDecimal overdueUnpaidAmount;
        private String nearestDueDate;
    }
}

