package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.ChangeRequestStateVO;
import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 孵化详情（API-B28、B29、B30）：B07 来源行、进度卡、当前步骤与结果。
 * current_step 为 1 / 2 / 3，已出雏为 0。
 */
public class IncubationDetailVO {

    private long incubationId;
    private long breedingBatchId;
    @Nullable
    private String farmName;
    @Nullable
    private String flockText;
    @Nullable
    private String breedName;
    private int incubationDays;
    @Nullable
    private String setDate;
    @Nullable
    private String expectedHatchDate;
    private int currentStep;
    private int setCount;
    private int daysToHatch;
    private int version;
    /** INCUBATION_STATUS */
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    @Nullable
    private String reminderText;
    @Nullable
    private String candleDate;
    @Nullable
    private Integer fertileCount;
    @Nullable
    private BigDecimal fertilityRate;
    @Nullable
    private String hatchDate;
    @Nullable
    private Integer hatchedCount;
    @Nullable
    private Integer healthyCount;
    @Nullable
    private Integer weakCount;
    @Nullable
    private BigDecimal hatchRate;
    @Nullable
    private Boolean transferToBatch;
    @Nullable
    private Long targetPenId;
    @Nullable
    private String targetPenName;
    @Nullable
    private String offspringBreedName;
    @Nullable
    private Long batchId;
    @Nullable
    private String batchNo;
    @Nullable
    private List<TimelineItem> timeline;
    private boolean canEdit;
    private boolean canRequestChange;
    @Nullable
    private ChangeRequestStateVO changeState;

    public long getIncubationId() {
        return incubationId;
    }

    public long getBreedingBatchId() {
        return breedingBatchId;
    }

    @Nullable
    public String getFarmName() {
        return farmName;
    }

    @Nullable
    public String getFlockText() {
        return flockText;
    }

    @Nullable
    public String getBreedName() {
        return breedName;
    }

    public int getIncubationDays() {
        return incubationDays;
    }

    @Nullable
    public String getSetDate() {
        return setDate;
    }

    @Nullable
    public String getExpectedHatchDate() {
        return expectedHatchDate;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public int getSetCount() {
        return setCount;
    }

    public int getDaysToHatch() {
        return daysToHatch;
    }

    public int getVersion() {
        return version;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }

    @Nullable
    public String getReminderText() {
        return reminderText;
    }

    @Nullable
    public String getCandleDate() {
        return candleDate;
    }

    @Nullable
    public Integer getFertileCount() {
        return fertileCount;
    }

    @Nullable
    public BigDecimal getFertilityRate() {
        return fertilityRate;
    }

    @Nullable
    public String getHatchDate() {
        return hatchDate;
    }

    @Nullable
    public Integer getHatchedCount() {
        return hatchedCount;
    }

    @Nullable
    public Integer getHealthyCount() {
        return healthyCount;
    }

    @Nullable
    public Integer getWeakCount() {
        return weakCount;
    }

    @Nullable
    public BigDecimal getHatchRate() {
        return hatchRate;
    }

    @Nullable
    public Boolean getTransferToBatch() {
        return transferToBatch;
    }

    @Nullable
    public Long getTargetPenId() {
        return targetPenId;
    }

    @Nullable
    public String getTargetPenName() {
        return targetPenName;
    }

    @Nullable
    public String getOffspringBreedName() {
        return offspringBreedName;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @NonNull
    public List<TimelineItem> getTimeline() {
        return timeline == null ? Collections.emptyList() : timeline;
    }

    public boolean isCanEdit() {
        return canEdit;
    }

    public boolean isCanRequestChange() {
        return canRequestChange;
    }

    @Nullable
    public ChangeRequestStateVO getChangeState() {
        return changeState;
    }

    /** 步骤登记记录（待定事项 5：本步数值字段未定义，暂按 detail 顶层字段显示） */
    public static class TimelineItem {

        /** SET / CANDLE / HATCH */
        @Nullable
        private String step;
        @Nullable
        private String stepText;
        @Nullable
        private String registeredAt;
        @Nullable
        private UserBriefVO registrant;

        @Nullable
        public String getStep() {
            return step;
        }

        @Nullable
        public String getStepText() {
            return stepText;
        }

        @Nullable
        public String getRegisteredAt() {
            return registeredAt;
        }

        @Nullable
        public UserBriefVO getRegistrant() {
            return registrant;
        }
    }
}
