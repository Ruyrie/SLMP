package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-D19 用药对象下拉：BATCH 时为批次项，PEN 时为栏舍项（含在栏批次）。员工只列负责栏舍。
 */
public class MedicationTargetVO {

    @Nullable
    private String penName;
    @Nullable
    private Long batchId;
    @Nullable
    private Long penId;
    @Nullable
    private Long categoryId;
    @Nullable
    private Integer currentCount;
    @Nullable
    private Boolean hasBatch;
    @Nullable
    private String batchNo;
    @Nullable
    private List<BatchItem> batches;

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public Long getPenId() {
        return penId;
    }

    @Nullable
    public Long getCategoryId() {
        return categoryId;
    }

    @Nullable
    public Integer getCurrentCount() {
        return currentCount;
    }

    @Nullable
    public Boolean getHasBatch() {
        return hasBatch;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @NonNull
    public List<BatchItem> getBatches() {
        return batches == null ? Collections.emptyList() : batches;
    }

    /** 栏舍内的在栏批次 */
    public static class BatchItem {

        private long batchId;
        @Nullable
        private String batchNo;
        private int stockCount;

        public long getBatchId() {
            return batchId;
        }

        @Nullable
        public String getBatchNo() {
            return batchNo;
        }

        public int getStockCount() {
            return stockCount;
        }
    }
}
