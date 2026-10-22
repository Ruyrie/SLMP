package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/** 对比批次选项（API-G26）：本场同品种、排除本批次，在养与已结束都列。 */
public class GrowthCompareOptionVO {

    private Long batchId;
    private String batchNo;
    private String penNames;
    private String status;
    private BigDecimal latestAvgWeight;
    private Integer latestDayAge;

    public long getBatchId() {
        return batchId == null ? 0L : batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getPenNames() {
        return penNames;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public BigDecimal getLatestAvgWeight() {
        return latestAvgWeight;
    }

    @Nullable
    public Integer getLatestDayAge() {
        return latestDayAge;
    }

    public boolean isActive() {
        return status == null || WeighBatchOptionVO.STATUS_ACTIVE.equals(status);
    }
}
