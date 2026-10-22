package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

/**
 * API-L18 批次入栏结果（L09 显示批次号、二维码与入栏摘要）。
 */
public class BatchEntryResultVO {

    private long batchId;
    private long penId;
    private String batchNo;
    private String qrContent;
    private String labelText;
    private String farmName;
    private String penName;
    private String breedName;
    private String purpose;
    private String entryDate;
    private int entryCount;
    private int entryAgeDays;
    private int penStockAfter;
    private int capacity;
    private int overCapacityCount;
    private String unitText;

    public long getBatchId() { return batchId; }

    public long getPenId() { return penId; }

    public String getBatchNo() { return batchNo; }

    public String getQrContent() { return qrContent; }

    /** “东山猪场 · 4 号育肥舍 · 杜长大” */
    public String getLabelText() { return labelText; }

    public String getFarmName() { return farmName; }

    public String getPenName() { return penName; }

    public String getBreedName() { return breedName; }

    public String getPurpose() { return purpose; }

    public String getEntryDate() { return entryDate; }

    public int getEntryCount() { return entryCount; }

    public int getEntryAgeDays() { return entryAgeDays; }

    /** 入栏后栏舍在栏数 */
    public int getPenStockAfter() { return penStockAfter; }

    public int getCapacity() { return capacity; }

    public int getOverCapacityCount() { return overCapacityCount; }

    @Nullable
    public String getUnitText() { return unitText; }
}
