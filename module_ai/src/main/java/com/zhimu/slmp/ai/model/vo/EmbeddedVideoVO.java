package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 知识原文内嵌的教学视频
 */
public class EmbeddedVideoVO {

    private long courseId;
    private String title;
    private int durationSec;
    private String coverUrl;

    public long getCourseId() {
        return courseId;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    public int getDurationSec() {
        return durationSec;
    }

    @Nullable
    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    public void setDurationSec(int durationSec) {
        this.durationSec = durationSec;
    }

    public void setCoverUrl(@Nullable String coverUrl) {
        this.coverUrl = coverUrl;
    }
}
