package com.zhimu.slmp.herd.model.vo;

/**
 * 批次所在栏舍及在栏数（BatchListItemVO、BatchDetailVO、TransferResultVO 共用）。
 */
public class BatchPenItem {

    private long penId;
    private String penName;
    private int stockCount;

    public long getPenId() { return penId; }

    public String getPenName() { return penName; }

    public int getStockCount() { return stockCount; }
}
