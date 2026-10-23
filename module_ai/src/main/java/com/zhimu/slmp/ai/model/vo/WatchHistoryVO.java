package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-K21 观看记录
 */
public class WatchHistoryVO extends CourseCardVO {

    private String lastWatchedAt;

    @Nullable
    public String getLastWatchedAt() {
        return lastWatchedAt;
    }

    public void setLastWatchedAt(@Nullable String lastWatchedAt) {
        this.lastWatchedAt = lastWatchedAt;
    }
}
