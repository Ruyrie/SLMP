package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-K02 / K04 会话详情；batch 仅 batch_link_state 为 VALID 时返回
 */
public class AiSessionDetailVO {

    private long id;
    private String title;
    private int roundCount;
    private boolean hasCorrection;
    private boolean contextTruncated;
    private boolean hasEpidemicAlert;
    private String batchLinkState;
    private int lastRoundNo;
    private LinkedBatchVO batch;
    private String invalidBatchNo;

    public long getId() {
        return id;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    public int getRoundCount() {
        return roundCount;
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

    @Nullable
    public String getBatchLinkState() {
        return batchLinkState;
    }

    public int getLastRoundNo() {
        return lastRoundNo;
    }

    @Nullable
    public LinkedBatchVO getBatch() {
        return batch;
    }

    @Nullable
    public String getInvalidBatchNo() {
        return invalidBatchNo;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    public void setRoundCount(int roundCount) {
        this.roundCount = roundCount;
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

    public void setBatchLinkState(@Nullable String batchLinkState) {
        this.batchLinkState = batchLinkState;
    }

    public void setLastRoundNo(int lastRoundNo) {
        this.lastRoundNo = lastRoundNo;
    }

    public void setBatch(@Nullable LinkedBatchVO batch) {
        this.batch = batch;
    }

    public void setInvalidBatchNo(@Nullable String invalidBatchNo) {
        this.invalidBatchNo = invalidBatchNo;
    }
}
