package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * API-D21 预估可出栏日期与预计用量（参数不全时只返回 ready = false）。
 */
public class ReleaseDatePreviewVO {

    private boolean ready;
    @Nullable
    private String lastDate;
    @Nullable
    private String releaseDate;
    @Nullable
    private String releaseText;
    @Nullable
    private String usedUnit;
    @Nullable
    private BigDecimal estimatedQty;
    @Nullable
    private Boolean overDose;
    @Nullable
    private Boolean categoryMismatch;
    @Nullable
    private List<PerBatch> perBatch;

    public boolean isReady() {
        return ready;
    }

    @Nullable
    public String getLastDate() {
        return lastDate;
    }

    @Nullable
    public String getReleaseDate() {
        return releaseDate;
    }

    @Nullable
    public String getReleaseText() {
        return releaseText;
    }

    @Nullable
    public String getUsedUnit() {
        return usedUnit;
    }

    @Nullable
    public BigDecimal getEstimatedQty() {
        return estimatedQty;
    }

    @Nullable
    public Boolean getOverDose() {
        return overDose;
    }

    @Nullable
    public Boolean getCategoryMismatch() {
        return categoryMismatch;
    }

    @NonNull
    public List<PerBatch> getPerBatch() {
        return perBatch == null ? Collections.emptyList() : perBatch;
    }

    /** 按栏舍登记时各批次的预估 */
    public static class PerBatch {

        private long batchId;
        @Nullable
        private String batchNo;
        @Nullable
        private String releaseDate;
        @Nullable
        private BigDecimal estimatedQty;

        public long getBatchId() {
            return batchId;
        }

        @Nullable
        public String getBatchNo() {
            return batchNo;
        }

        @Nullable
        public String getReleaseDate() {
            return releaseDate;
        }

        @Nullable
        public BigDecimal getEstimatedQty() {
            return estimatedQty;
        }
    }
}
