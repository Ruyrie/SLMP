package com.zhimu.server.biz.finance.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.api.PageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 批次核算（API-E26–E28）响应结构。 */
public final class SettlementVos {

    private SettlementVos() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class OptionPageVO extends PageVO<OptionVO> {
        private Integer farmPendingSupplementCount;
    }

    @Data
    public static class OptionVO {
        private Long batchId;
        private String batchNo;
        private String penName;
        private String batchStatus;
        private String settlementStatus;
        private Integer pendingSupplementCount;
    }

    @Data
    public static class BatchSettlementVO {
        private Long settlementId;
        private String status;
        private String settledAt;
        private String calcAt;
        @ManagerView
        private BigDecimal profit;
        @ManagerView
        private BigDecimal saleIncome;
        @ManagerView
        private BigDecimal receivedAmount;
        @ManagerView
        private BigDecimal costTotal;
        private String perHeadBasis;
        private Integer perHeadCount;
        private Integer version;
        private SettlementBatch batch;
        @ManagerView
        private BigDecimal costPerHead;
        @ManagerView
        private BigDecimal profitPerHead;
        private BigDecimal fcr;
        private BigDecimal feedKg;
        private BigDecimal netGainKg;
        private List<CostItem> costs = new ArrayList<>();
        private List<SettlementSaleOrder> saleOrders = new ArrayList<>();
        private List<Supplement> supplements = new ArrayList<>();
        private PreviewAfter previewAfter;
        private List<CostRecord> recentCostRecords = new ArrayList<>();
    }

    @Data
    public static class SettlementBatch {
        private Long batchId;
        private String batchNo;
        private String penName;
        private String breedName;
        private String categoryName;
        private String countUnitText;
        private Integer dayAge;
        private Integer entryCount;
        private Integer deathCount;
        private Integer soldCount;
        private Integer currentCount;
        private String endDate;
    }

    @Data
    public static class CostItem {
        private String costGroup;
        private String costGroupText;
        @ManagerView
        private BigDecimal amount;
        private BigDecimal shareRate;
        private String note;
    }

    @Data
    public static class SettlementSaleOrder {
        private Long id;
        private String saleDate;
        private Integer quantity;
        @ManagerView
        private BigDecimal totalWeight;
        @ManagerView
        private BigDecimal unitPrice;
        private String paymentStatus;
        @ManagerView
        private BigDecimal saleAmount;
    }

    @Data
    public static class Supplement {
        private Long id;
        private String bizDate;
        private String title;
        @ManagerView
        private BigDecimal amount;
        private String costGroup;
    }

    @Data
    public static class PreviewAfter {
        @ManagerView
        private BigDecimal costOther;
        @ManagerView
        private BigDecimal costTotal;
        @ManagerView
        private BigDecimal profit;
    }

    @Data
    public static class CostRecord {
        private String title;
        private String bizDate;
        @ManagerView
        private BigDecimal amount;
        private String costGroupText;
    }
}
