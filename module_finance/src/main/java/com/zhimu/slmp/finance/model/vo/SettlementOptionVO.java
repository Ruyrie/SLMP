package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

/**
 * API-E26 批次选择项（E03 关联批次、E13 按批次）
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class SettlementOptionVO {

    private long batchId;
    private String batchNo;
    @Nullable
    private String penName;
    /** ACTIVE / ENDED */
    private String batchStatus;
    /** RUNNING / LOCKED */
    @Nullable
    private String settlementStatus;
    private int pendingSupplementCount;

    public long getBatchId() {
        return batchId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    public String getBatchStatus() {
        return batchStatus;
    }

    @Nullable
    public String getSettlementStatus() {
        return settlementStatus;
    }

    public int getPendingSupplementCount() {
        return pendingSupplementCount;
    }
}
