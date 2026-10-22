package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * API-L23、L24 死淘结果；message 为成功提示“已登记死淘 10 头，存栏已更新”。
 */
public class DeathResultVO {

    private long eventId;
    private long batchId;
    private int quantity;
    private int currentCount;
    private int deathCount;
    private BigDecimal mortalityRate;
    private String batchStatus;
    private String message;
    private boolean batchEnded;

    public long getEventId() { return eventId; }

    public long getBatchId() { return batchId; }

    public int getQuantity() { return quantity; }

    public int getCurrentCount() { return currentCount; }

    public int getDeathCount() { return deathCount; }

    @Nullable
    public BigDecimal getMortalityRate() { return mortalityRate; }

    public String getBatchStatus() { return batchStatus; }

    @Nullable
    public String getMessage() { return message; }

    public boolean isBatchEnded() { return batchEnded; }
}
