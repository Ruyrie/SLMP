package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SSE meta：轮次落库后第一条
 */
public class StreamMetaVO {

    private long messageId;
    private long sessionId;
    private int roundNo;
    private boolean sessionCreated;
    private String batchLinkState;

    public long getMessageId() {
        return messageId;
    }

    public long getSessionId() {
        return sessionId;
    }

    public int getRoundNo() {
        return roundNo;
    }

    public boolean isSessionCreated() {
        return sessionCreated;
    }

    @Nullable
    public String getBatchLinkState() {
        return batchLinkState;
    }

    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public void setRoundNo(int roundNo) {
        this.roundNo = roundNo;
    }

    public void setSessionCreated(boolean sessionCreated) {
        this.sessionCreated = sessionCreated;
    }

    public void setBatchLinkState(@Nullable String batchLinkState) {
        this.batchLinkState = batchLinkState;
    }
}
