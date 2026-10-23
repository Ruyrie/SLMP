package com.zhimu.slmp.ai.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-K20 进度上报（event：START / TICK / PAUSE / SEEK / SWITCH / LEAVE / END）
 */
public class CourseProgressRequest {

    private String playToken;
    private String event;
    private int positionSec;
    private int watchedSec;
    private String quality;
    private String networkType;
    private String entryType;
    private String clientTime;
    private Boolean askAiClicked;
    private Boolean knowledgeClicked;

    @Nullable
    public String getPlayToken() {
        return playToken;
    }

    @Nullable
    public String getEvent() {
        return event;
    }

    public int getPositionSec() {
        return positionSec;
    }

    public int getWatchedSec() {
        return watchedSec;
    }

    @Nullable
    public String getQuality() {
        return quality;
    }

    @Nullable
    public String getNetworkType() {
        return networkType;
    }

    @Nullable
    public String getEntryType() {
        return entryType;
    }

    @Nullable
    public String getClientTime() {
        return clientTime;
    }

    @Nullable
    public Boolean getAskAiClicked() {
        return askAiClicked;
    }

    @Nullable
    public Boolean getKnowledgeClicked() {
        return knowledgeClicked;
    }

    public void setPlayToken(@Nullable String playToken) {
        this.playToken = playToken;
    }

    public void setEvent(@Nullable String event) {
        this.event = event;
    }

    public void setPositionSec(int positionSec) {
        this.positionSec = positionSec;
    }

    public void setWatchedSec(int watchedSec) {
        this.watchedSec = watchedSec;
    }

    public void setQuality(@Nullable String quality) {
        this.quality = quality;
    }

    public void setNetworkType(@Nullable String networkType) {
        this.networkType = networkType;
    }

    public void setEntryType(@Nullable String entryType) {
        this.entryType = entryType;
    }

    public void setClientTime(@Nullable String clientTime) {
        this.clientTime = clientTime;
    }

    public void setAskAiClicked(@Nullable Boolean askAiClicked) {
        this.askAiClicked = askAiClicked;
    }

    public void setKnowledgeClicked(@Nullable Boolean knowledgeClicked) {
        this.knowledgeClicked = knowledgeClicked;
    }
}
