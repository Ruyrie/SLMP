package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

/**
 * API-L21、L22 转栏结果；message 为成功提示“已转栏 100 头到 4 号育肥舍”。
 */
public class TransferResultVO {

    private long eventId;
    private long batchId;
    private int quantity;
    private int overCapacityCount;
    private String message;
    private BatchPenItem fromPen;
    private BatchPenItem toPen;

    public long getEventId() { return eventId; }

    public long getBatchId() { return batchId; }

    public int getQuantity() { return quantity; }

    public int getOverCapacityCount() { return overCapacityCount; }

    @Nullable
    public String getMessage() { return message; }

    @Nullable
    public BatchPenItem getFromPen() { return fromPen; }

    @Nullable
    public BatchPenItem getToPen() { return toPen; }
}
