package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * G05 称重表单上下文（API-G19，按所选日期计算）：所选日期已有记录时 existing_record_id 不为空（客户端切换为修改）；
 * 批次已结束时 can_register 为 false 并给 block_message。
 */
public class WeighFormVO {

    private WeighFormBatchVO batch;
    private String weighDate;
    private Integer dayAge;
    private Integer stockCount;
    private Boolean firstWeigh;
    private WeighLastVO lastWeigh;
    private Long existingRecordId;
    private Boolean canRegister;
    private String blockMessage;
    private Boolean stdAvailable;
    private List<StdPointVO> stdPoints;
    private BigDecimal stdWeightAtDate;
    private BigDecimal allowedDeviation;
    private String currentGrowthStatus;
    private BigDecimal sampleRatio;
    private Integer suggestedSample;
    private WeighCycleVO weighCycle;
    private Boolean canEditCycle;

    @Nullable
    public WeighFormBatchVO getBatch() {
        return batch;
    }

    @Nullable
    public String getWeighDate() {
        return weighDate;
    }

    @Nullable
    public Integer getDayAge() {
        return dayAge;
    }

    @Nullable
    public Integer getStockCount() {
        return stockCount;
    }

    public boolean isFirstWeigh() {
        return Boolean.TRUE.equals(firstWeigh);
    }

    @Nullable
    public WeighLastVO getLastWeigh() {
        return lastWeigh;
    }

    @Nullable
    public Long getExistingRecordId() {
        return existingRecordId;
    }

    public boolean isCanRegister() {
        return !Boolean.FALSE.equals(canRegister);
    }

    @Nullable
    public String getBlockMessage() {
        return blockMessage;
    }

    public boolean isStdAvailable() {
        return Boolean.TRUE.equals(stdAvailable);
    }

    @NonNull
    public List<StdPointVO> getStdPoints() {
        return stdPoints == null ? Collections.emptyList() : stdPoints;
    }

    @Nullable
    public BigDecimal getStdWeightAtDate() {
        return stdWeightAtDate;
    }

    @Nullable
    public BigDecimal getAllowedDeviation() {
        return allowedDeviation;
    }

    @Nullable
    public String getCurrentGrowthStatus() {
        return currentGrowthStatus;
    }

    @Nullable
    public BigDecimal getSampleRatio() {
        return sampleRatio;
    }

    @Nullable
    public Integer getSuggestedSample() {
        return suggestedSample;
    }

    @Nullable
    public WeighCycleVO getWeighCycle() {
        return weighCycle;
    }

    public boolean isCanEditCycle() {
        return Boolean.TRUE.equals(canEditCycle);
    }

    /** 上次称重：date、avg_weight */
    public static class WeighLastVO {
        private String date;
        private BigDecimal avgWeight;

        @Nullable
        public String getDate() {
            return date;
        }

        @Nullable
        public BigDecimal getAvgWeight() {
            return avgWeight;
        }
    }
}
