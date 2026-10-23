package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-K01 历史会话列表项（K02）
 */
public class AiSessionItemVO {

    private long id;
    private String title;
    private String topic;
    private String topicText;
    private String batchNo;
    private int roundCount;
    private String lastAskedAt;
    private String dateGroup;
    private String dateGroupText;
    private boolean hasCorrection;
    private boolean contextTruncated;
    private boolean hasEpidemicAlert;

    public long getId() {
        return id;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getTopic() {
        return topic;
    }

    @Nullable
    public String getTopicText() {
        return topicText;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    public int getRoundCount() {
        return roundCount;
    }

    @Nullable
    public String getLastAskedAt() {
        return lastAskedAt;
    }

    @Nullable
    public String getDateGroup() {
        return dateGroup;
    }

    @Nullable
    public String getDateGroupText() {
        return dateGroupText;
    }

    public boolean isHasCorrection() {
        return hasCorrection;
    }

    public boolean isContextTruncated() {
        return contextTruncated;
    }

    public boolean isHasEpidemicAlert() {
        return hasEpidemicAlert;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    public void setTopic(@Nullable String topic) {
        this.topic = topic;
    }

    public void setTopicText(@Nullable String topicText) {
        this.topicText = topicText;
    }

    public void setBatchNo(@Nullable String batchNo) {
        this.batchNo = batchNo;
    }

    public void setRoundCount(int roundCount) {
        this.roundCount = roundCount;
    }

    public void setLastAskedAt(@Nullable String lastAskedAt) {
        this.lastAskedAt = lastAskedAt;
    }

    public void setDateGroup(@Nullable String dateGroup) {
        this.dateGroup = dateGroup;
    }

    public void setDateGroupText(@Nullable String dateGroupText) {
        this.dateGroupText = dateGroupText;
    }

    public void setHasCorrection(boolean hasCorrection) {
        this.hasCorrection = hasCorrection;
    }

    public void setContextTruncated(boolean contextTruncated) {
        this.contextTruncated = contextTruncated;
    }

    public void setHasEpidemicAlert(boolean hasEpidemicAlert) {
        this.hasEpidemicAlert = hasEpidemicAlert;
    }
}
