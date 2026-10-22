package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/** 称重批次选项（API-G18）：status 为 ACTIVE 时在养；员工只列负责栏舍的批次。 */
public class WeighBatchOptionVO {

    public static final String STATUS_ACTIVE = "ACTIVE";

    private Long batchId;
    private String batchNo;
    private String penNames;
    private String breedName;
    private Integer dayAge;
    private String status;
    private BigDecimal lastAvgWeight;
    private String growthStatus;
    private String growthStatusText;

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
    public String getBreedName() {
        return breedName;
    }

    @Nullable
    public Integer getDayAge() {
        return dayAge;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public BigDecimal getLastAvgWeight() {
        return lastAvgWeight;
    }

    @Nullable
    public String getGrowthStatus() {
        return growthStatus;
    }

    @Nullable
    public String getGrowthStatusText() {
        return growthStatusText;
    }

    public boolean isActive() {
        return status == null || STATUS_ACTIVE.equals(status);
    }
}
