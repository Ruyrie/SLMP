package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-K16 专题卡
 */
public class TopicVO {

    private long id;
    private String name;
    private String categoryName;
    private String coverUrl;
    private int courseCount;
    private int totalDurationSec;
    private int learnedCount;
    private String lastLearnedAt;

    public long getId() {
        return id;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getCategoryName() {
        return categoryName;
    }

    @Nullable
    public String getCoverUrl() {
        return coverUrl;
    }

    public int getCourseCount() {
        return courseCount;
    }

    public int getTotalDurationSec() {
        return totalDurationSec;
    }

    public int getLearnedCount() {
        return learnedCount;
    }

    @Nullable
    public String getLastLearnedAt() {
        return lastLearnedAt;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public void setCategoryName(@Nullable String categoryName) {
        this.categoryName = categoryName;
    }

    public void setCoverUrl(@Nullable String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public void setCourseCount(int courseCount) {
        this.courseCount = courseCount;
    }

    public void setTotalDurationSec(int totalDurationSec) {
        this.totalDurationSec = totalDurationSec;
    }

    public void setLearnedCount(int learnedCount) {
        this.learnedCount = learnedCount;
    }

    public void setLastLearnedAt(@Nullable String lastLearnedAt) {
        this.lastLearnedAt = lastLearnedAt;
    }
}
