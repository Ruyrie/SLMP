package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.ChangeRequestStateVO;
import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/** 称重记录（API-G20–G22）：growth_change、todo_completed、next_weigh_date 只在新增、修改时返回。 */
public class WeighRecordVO {

    public static final String MODE_SAMPLE = "SAMPLE";
    public static final String MODE_INDIVIDUAL = "INDIVIDUAL";

    private Long id;
    private Long batchId;
    private String batchNo;
    private String weighDate;
    private Integer dayAge;
    private Integer stockCount;
    private String mode;
    private Integer sampleCount;
    private BigDecimal totalWeight;
    private List<WeighItemVO> items;
    private BigDecimal avgWeight;
    private Integer suggestedSample;
    private Boolean sampleLow;
    private BigDecimal stdWeight;
    private BigDecimal allowedDeviation;
    private BigDecimal deviationRatio;
    private Boolean outOfRange;
    private String growthResult;
    private String growthResultText;
    private String gainBaseType;
    private String gainBaseDate;
    private BigDecimal gainBaseWeight;
    private BigDecimal dailyGain;
    private UserBriefVO recorder;
    private String createdAt;
    private ChangeRequestStateVO changeState;
    private Boolean canRequestChange;
    private List<String> changeFields;
    private List<String> recalcTags;
    private String growthChange;
    private Boolean todoCompleted;
    private String nextWeighDate;
    private Integer version;

    public long getId() {
        return id == null ? 0L : id;
    }

    public long getBatchId() {
        return batchId == null ? 0L : batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
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

    @Nullable
    public String getMode() {
        return mode;
    }

    @Nullable
    public Integer getSampleCount() {
        return sampleCount;
    }

    @Nullable
    public BigDecimal getTotalWeight() {
        return totalWeight;
    }

    @NonNull
    public List<WeighItemVO> getItems() {
        return items == null ? Collections.emptyList() : items;
    }

    @Nullable
    public BigDecimal getAvgWeight() {
        return avgWeight;
    }

    @Nullable
    public Integer getSuggestedSample() {
        return suggestedSample;
    }

    public boolean isSampleLow() {
        return Boolean.TRUE.equals(sampleLow);
    }

    @Nullable
    public BigDecimal getStdWeight() {
        return stdWeight;
    }

    @Nullable
    public BigDecimal getAllowedDeviation() {
        return allowedDeviation;
    }

    @Nullable
    public BigDecimal getDeviationRatio() {
        return deviationRatio;
    }

    public boolean isOutOfRange() {
        return Boolean.TRUE.equals(outOfRange);
    }

    @Nullable
    public String getGrowthResult() {
        return growthResult;
    }

    @Nullable
    public String getGrowthResultText() {
        return growthResultText;
    }

    @Nullable
    public String getGainBaseType() {
        return gainBaseType;
    }

    @Nullable
    public String getGainBaseDate() {
        return gainBaseDate;
    }

    @Nullable
    public BigDecimal getGainBaseWeight() {
        return gainBaseWeight;
    }

    @Nullable
    public BigDecimal getDailyGain() {
        return dailyGain;
    }

    @Nullable
    public UserBriefVO getRecorder() {
        return recorder;
    }

    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public ChangeRequestStateVO getChangeState() {
        return changeState;
    }

    public boolean isCanRequestChange() {
        return Boolean.TRUE.equals(canRequestChange);
    }

    @NonNull
    public List<String> getChangeFields() {
        return changeFields == null ? Collections.emptyList() : changeFields;
    }

    @NonNull
    public List<String> getRecalcTags() {
        return recalcTags == null ? Collections.emptyList() : recalcTags;
    }

    @Nullable
    public String getGrowthChange() {
        return growthChange;
    }

    @Nullable
    public Boolean getTodoCompleted() {
        return todoCompleted;
    }

    @Nullable
    public String getNextWeighDate() {
        return nextWeighDate;
    }

    public int getVersion() {
        return version == null ? 0 : version;
    }

    /** 逐头录入的一头：seq_no、weight */
    public static class WeighItemVO {
        private Integer seqNo;
        private BigDecimal weight;

        public int getSeqNo() {
            return seqNo == null ? 0 : seqNo;
        }

        @Nullable
        public BigDecimal getWeight() {
            return weight;
        }
    }
}
