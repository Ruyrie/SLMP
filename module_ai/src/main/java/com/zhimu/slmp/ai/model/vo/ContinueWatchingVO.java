package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.math.BigDecimal;

/**
 * API-K15 继续观看（没有在看的视频时 data 为 null）
 */
public class ContinueWatchingVO {

    private long courseId;
    private String title;
    private String coverUrl;
    private String categoryName;
    private int durationSec;
    private int positionSec;
    private int remainingSec;
    private BigDecimal progressRatio;
    private String topicName;
    private Integer sectionNo;

    public long getCourseId() {
        return courseId;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getCoverUrl() {
        return coverUrl;
    }

    @Nullable
    public String getCategoryName() {
        return categoryName;
    }

    public int getDurationSec() {
        return durationSec;
    }

    public int getPositionSec() {
        return positionSec;
    }

    public int getRemainingSec() {
        return remainingSec;
    }

    @Nullable
    public BigDecimal getProgressRatio() {
        return progressRatio;
    }

    @Nullable
    public String getTopicName() {
        return topicName;
    }

    @Nullable
    public Integer getSectionNo() {
        return sectionNo;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    public void setCoverUrl(@Nullable String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public void setCategoryName(@Nullable String categoryName) {
        this.categoryName = categoryName;
    }

    public void setDurationSec(int durationSec) {
        this.durationSec = durationSec;
    }

    public void setPositionSec(int positionSec) {
        this.positionSec = positionSec;
    }

    public void setRemainingSec(int remainingSec) {
        this.remainingSec = remainingSec;
    }

    public void setProgressRatio(@Nullable BigDecimal progressRatio) {
        this.progressRatio = progressRatio;
    }

    public void setTopicName(@Nullable String topicName) {
        this.topicName = topicName;
    }

    public void setSectionNo(@Nullable Integer sectionNo) {
        this.sectionNo = sectionNo;
    }
}
