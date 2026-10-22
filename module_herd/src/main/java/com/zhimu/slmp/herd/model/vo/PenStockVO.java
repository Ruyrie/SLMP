package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

/**
 * API-L17 批次各栏舍在栏数（只含 stock_count 大于 0 的栏舍；员工只返回负责栏舍）。
 */
public class PenStockVO {

    private long penId;
    private String penName;
    private String firstInDate;
    private int stockCount;
    private int capacity;
    private String unitText;

    public long getPenId() { return penId; }

    public String getPenName() { return penName; }

    /** 批次进入该栏舍的日期 */
    @Nullable
    public String getFirstInDate() { return firstInDate; }

    public int getStockCount() { return stockCount; }

    public int getCapacity() { return capacity; }

    @Nullable
    public String getUnitText() { return unitText; }
}
