package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.math.BigDecimal;

/**
 * API-K20 进度上报结果
 */
public class ProgressResultVO {

    private int positionSec;
    private int maxPositionSec;
    private BigDecimal progressRatio;
    private boolean completed;
    private boolean justCompleted;
    private String learnStatus;
    private Integer topicLearnedCount;

    public int getPositionSec() {
        return positionSec;
    }

    public int getMaxPositionSec() {
        return maxPositionSec;
    }

    @Nullable
    public BigDecimal getProgressRatio() {
        return progressRatio;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isJustCompleted() {
        return justCompleted;
    }

    @Nullable
    public String getLearnStatus() {
        return learnStatus;
    }

    @Nullable
    public Integer getTopicLearnedCount() {
        return topicLearnedCount;
    }

    public void setPositionSec(int positionSec) {
        this.positionSec = positionSec;
    }

    public void setMaxPositionSec(int maxPositionSec) {
        this.maxPositionSec = maxPositionSec;
    }

    public void setProgressRatio(@Nullable BigDecimal progressRatio) {
        this.progressRatio = progressRatio;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void setJustCompleted(boolean justCompleted) {
        this.justCompleted = justCompleted;
    }

    public void setLearnStatus(@Nullable String learnStatus) {
        this.learnStatus = learnStatus;
    }

    public void setTopicLearnedCount(@Nullable Integer topicLearnedCount) {
        this.topicLearnedCount = topicLearnedCount;
    }
}
