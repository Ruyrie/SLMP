package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/** G06 称重记录列表项（API-G25，按称重日期倒序）：action 为 EDIT / REQUEST_CHANGE / NONE。 */
public class WeighRecordItemVO {

    public static final String ACTION_EDIT = "EDIT";
    public static final String ACTION_REQUEST_CHANGE = "REQUEST_CHANGE";
    public static final String ACTION_NONE = "NONE";

    private Long id;
    private String weighDate;
    private Integer dayAge;
    private BigDecimal avgWeight;
    private Integer sampleCount;
    private String mode;
    private String recorderName;
    private BigDecimal stdWeight;
    private BigDecimal deviationRatio;
    private Boolean outOfRange;
    private BigDecimal dailyGain;
    private String action;

    public long getId() {
        return id == null ? 0L : id;
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
    public BigDecimal getAvgWeight() {
        return avgWeight;
    }

    @Nullable
    public Integer getSampleCount() {
        return sampleCount;
    }

    @Nullable
    public String getMode() {
        return mode;
    }

    @Nullable
    public String getRecorderName() {
        return recorderName;
    }

    @Nullable
    public BigDecimal getStdWeight() {
        return stdWeight;
    }

    @Nullable
    public BigDecimal getDeviationRatio() {
        return deviationRatio;
    }

    public boolean isOutOfRange() {
        return Boolean.TRUE.equals(outOfRange);
    }

    @Nullable
    public BigDecimal getDailyGain() {
        return dailyGain;
    }

    @Nullable
    public String getAction() {
        return action;
    }

    public boolean isClickable() {
        return action != null && !ACTION_NONE.equals(action);
    }
}
