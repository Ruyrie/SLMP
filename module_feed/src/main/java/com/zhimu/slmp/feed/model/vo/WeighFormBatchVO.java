package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 称重表单与生长曲线中的批次（WeighFormVO.batch、GrowthCurveVO.batch）：
 * batch_id、batch_no、pen_names、breed_name、current_count、entry_date、entry_age_days、entry_avg_weight（可空）、status；
 * 生长曲线另带 day_age。
 */
public class WeighFormBatchVO {

    private Long batchId;
    private String batchNo;
    private String penNames;
    private String breedName;
    private Integer dayAge;
    private Integer currentCount;
    private String entryDate;
    private Integer entryAgeDays;
    private BigDecimal entryAvgWeight;
    private String status;

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
    public Integer getCurrentCount() {
        return currentCount;
    }

    @Nullable
    public String getEntryDate() {
        return entryDate;
    }

    @Nullable
    public Integer getEntryAgeDays() {
        return entryAgeDays;
    }

    @Nullable
    public BigDecimal getEntryAvgWeight() {
        return entryAvgWeight;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == null || WeighBatchOptionVO.STATUS_ACTIVE.equals(status);
    }
}
