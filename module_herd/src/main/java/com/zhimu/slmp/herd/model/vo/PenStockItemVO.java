package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * API-L04 按栏舍存栏（L03 栏舍视图列表项）。
 */
public class PenStockItemVO {

    public static final String STATUS_IN_USE = "IN_USE";
    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_DISABLED = "DISABLED";

    private long penId;
    private String penCode;
    private String penName;
    private String penType;
    private String penTypeText;
    private String categoryName;
    private int capacity;
    private int stockCount;
    private boolean isBreedingPen;
    private boolean inWithdrawal;
    private String displayStatus;
    private BigDecimal occupancyRate;
    private BigDecimal mortalityRate;
    private Integer batchCount;
    private PenFirstBatchItem firstBatch;
    private EndedBatchItem lastEndedBatch;
    private String unitText;

    public long getPenId() { return penId; }

    public String getPenCode() { return penCode; }

    public String getPenName() { return penName; }

    public String getPenType() { return penType; }

    @Nullable
    public String getPenTypeText() { return penTypeText; }

    public String getCategoryName() { return categoryName; }

    public int getCapacity() { return capacity; }

    public int getStockCount() { return stockCount; }

    public boolean isBreedingPen() { return isBreedingPen; }

    public boolean isInWithdrawal() { return inWithdrawal; }

    /** IN_USE / EMPTY / DISABLED */
    public String getDisplayStatus() { return displayStatus; }

    @Nullable
    public BigDecimal getOccupancyRate() { return occupancyRate; }

    @Nullable
    public BigDecimal getMortalityRate() { return mortalityRate; }

    @Nullable
    public Integer getBatchCount() { return batchCount; }

    @Nullable
    public PenFirstBatchItem getFirstBatch() { return firstBatch; }

    @Nullable
    public EndedBatchItem getLastEndedBatch() { return lastEndedBatch; }

    @Nullable
    public String getUnitText() { return unitText; }

    public boolean isDisabled() { return STATUS_DISABLED.equals(displayStatus); }

    public boolean isEmpty() { return STATUS_EMPTY.equals(displayStatus); }

    /** 栏舍内第一个在栏批次 */
    public static class PenFirstBatchItem {
        private long batchId;
        private String batchNo;
        private int dayAge;
        private String stageName;

        public long getBatchId() { return batchId; }

        public String getBatchNo() { return batchNo; }

        public int getDayAge() { return dayAge; }

        @Nullable
        public String getStageName() { return stageName; }
    }

    /** 空栏时最近结束的批次 */
    public static class EndedBatchItem {
        public static final String END_ALL_SOLD = "ALL_SOLD";
        public static final String END_ALL_DEAD = "ALL_DEAD";

        private String batchNo;
        private String endDate;
        private String endType;

        public String getBatchNo() { return batchNo; }

        public String getEndDate() { return endDate; }

        /** ALL_DEAD / ALL_SOLD */
        public String getEndType() { return endType; }
    }
}
