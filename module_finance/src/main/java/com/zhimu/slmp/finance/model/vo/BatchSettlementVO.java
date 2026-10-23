package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-E27、E28 批次核算
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class BatchSettlementVO {

    @Nullable
    private Long settlementId;
    /** RUNNING / LOCKED */
    private String status;
    @Nullable
    private String settledAt;
    @Nullable
    private String calcAt;
    private BigDecimal profit;
    private BigDecimal saleIncome;
    private BigDecimal receivedAmount;
    private BigDecimal costTotal;
    /** SOLD / STOCK */
    private String perHeadBasis;
    private int perHeadCount;
    private int version;
    private SettlementBatch batch;
    @Nullable
    private BigDecimal costPerHead;
    @Nullable
    private BigDecimal profitPerHead;
    @Nullable
    private BigDecimal fcr;
    @Nullable
    private BigDecimal feedKg;
    @Nullable
    private BigDecimal netGainKg;
    private List<CostItem> costs;
    private List<SettlementSaleOrder> saleOrders;
    private List<Supplement> supplements;
    @Nullable
    private PreviewAfter previewAfter;
    private List<CostRecord> recentCostRecords;

    @Nullable
    public Long getSettlementId() {
        return settlementId;
    }

    public String getStatus() {
        return status;
    }

    @Nullable
    public String getSettledAt() {
        return settledAt;
    }

    @Nullable
    public String getCalcAt() {
        return calcAt;
    }

    public BigDecimal getProfit() {
        return profit;
    }

    public BigDecimal getSaleIncome() {
        return saleIncome;
    }

    public BigDecimal getReceivedAmount() {
        return receivedAmount;
    }

    public BigDecimal getCostTotal() {
        return costTotal;
    }

    public String getPerHeadBasis() {
        return perHeadBasis;
    }

    public int getPerHeadCount() {
        return perHeadCount;
    }

    public int getVersion() {
        return version;
    }

    public SettlementBatch getBatch() {
        return batch;
    }

    @Nullable
    public BigDecimal getCostPerHead() {
        return costPerHead;
    }

    @Nullable
    public BigDecimal getProfitPerHead() {
        return profitPerHead;
    }

    @Nullable
    public BigDecimal getFcr() {
        return fcr;
    }

    @Nullable
    public BigDecimal getFeedKg() {
        return feedKg;
    }

    @Nullable
    public BigDecimal getNetGainKg() {
        return netGainKg;
    }

    @NonNull
    public List<CostItem> getCosts() {
        return costs == null ? java.util.Collections.emptyList() : costs;
    }

    @NonNull
    public List<SettlementSaleOrder> getSaleOrders() {
        return saleOrders == null ? java.util.Collections.emptyList() : saleOrders;
    }

    @NonNull
    public List<Supplement> getSupplements() {
        return supplements == null ? java.util.Collections.emptyList() : supplements;
    }

    @Nullable
    public PreviewAfter getPreviewAfter() {
        return previewAfter;
    }

    @NonNull
    public List<CostRecord> getRecentCostRecords() {
        return recentCostRecords == null ? java.util.Collections.emptyList() : recentCostRecords;
    }

    /** 批次信息 */
    public static class SettlementBatch {

        private long batchId;
        private String batchNo;
        private String penName;
        @Nullable
        private String breedName;
        private String categoryName;
        @Nullable
        private String countUnitText;
        private int dayAge;
        private int entryCount;
        private int deathCount;
        private int soldCount;
        private int currentCount;
        @Nullable
        private String endDate;

        public long getBatchId() {
            return batchId;
        }

        public String getBatchNo() {
            return batchNo;
        }

        public String getPenName() {
            return penName;
        }

        @Nullable
        public String getBreedName() {
            return breedName;
        }

        public String getCategoryName() {
            return categoryName;
        }

        @Nullable
        public String getCountUnitText() {
            return countUnitText;
        }

        public int getDayAge() {
            return dayAge;
        }

        public int getEntryCount() {
            return entryCount;
        }

        public int getDeathCount() {
            return deathCount;
        }

        public int getSoldCount() {
            return soldCount;
        }

        public int getCurrentCount() {
            return currentCount;
        }

        @Nullable
        public String getEndDate() {
            return endDate;
        }
    }

    /** 成本构成（顺序固定：FEED、SEEDLING、DRUG、OTHER） */
    public static class CostItem {

        private String costGroup;
        private String costGroupText;
        private BigDecimal amount;
        @Nullable
        private BigDecimal shareRate;
        @Nullable
        private String note;

        public String getCostGroup() {
            return costGroup;
        }

        public String getCostGroupText() {
            return costGroupText;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        @Nullable
        public BigDecimal getShareRate() {
            return shareRate;
        }

        @Nullable
        public String getNote() {
            return note;
        }
    }

    /** 批次出栏单 */
    public static class SettlementSaleOrder {

        private long id;
        private String saleDate;
        private int quantity;
        private BigDecimal totalWeight;
        private BigDecimal unitPrice;
        private String paymentStatus;
        private BigDecimal saleAmount;

        public long getId() {
            return id;
        }

        public String getSaleDate() {
            return saleDate;
        }

        public int getQuantity() {
            return quantity;
        }

        public BigDecimal getTotalWeight() {
            return totalWeight;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public String getPaymentStatus() {
            return paymentStatus;
        }

        public BigDecimal getSaleAmount() {
            return saleAmount;
        }
    }

    /** 待确认的补录收支 */
    public static class Supplement {

        private long id;
        private String bizDate;
        private String title;
        private BigDecimal amount;
        private String costGroup;

        public long getId() {
            return id;
        }

        public String getBizDate() {
            return bizDate;
        }

        public String getTitle() {
            return title;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getCostGroup() {
            return costGroup;
        }
    }

    /** 确认补录后的预计结果 */
    public static class PreviewAfter {

        private BigDecimal costOther;
        private BigDecimal costTotal;
        private BigDecimal profit;

        public BigDecimal getCostOther() {
            return costOther;
        }

        public BigDecimal getCostTotal() {
            return costTotal;
        }

        public BigDecimal getProfit() {
            return profit;
        }
    }

    /** 最近计入成本的记录 */
    public static class CostRecord {

        private String title;
        private String bizDate;
        private BigDecimal amount;
        private String costGroupText;

        public String getTitle() {
            return title;
        }

        public String getBizDate() {
            return bizDate;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getCostGroupText() {
            return costGroupText;
        }
    }
}
