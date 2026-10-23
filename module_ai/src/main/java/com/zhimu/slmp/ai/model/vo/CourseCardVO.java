package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.math.BigDecimal;

/**
 * 课程卡（API-K18、K17 courses、K19 related_courses）。learn_status：NOT_STARTED / LEARNING / COMPLETED
 */
public class CourseCardVO {

    private long id;
    private String title;
    private String coverUrl;
    private int durationSec;
    private String publishedAt;
    private String categoryName;
    private String learnStatus;
    private int positionSec;
    private BigDecimal progressRatio;
    private Long topicId;
    private String topicName;
    private Integer sectionNo;

    public long getId() {
        return id;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getCoverUrl() {
        return coverUrl;
    }

    public int getDurationSec() {
        return durationSec;
    }

    @Nullable
    public String getPublishedAt() {
        return publishedAt;
    }

    @Nullable
    public String getCategoryName() {
        return categoryName;
    }

    @Nullable
    public String getLearnStatus() {
        return learnStatus;
    }

    public int getPositionSec() {
        return positionSec;
    }

    @Nullable
    public BigDecimal getProgressRatio() {
        return progressRatio;
    }

    @Nullable
    public Long getTopicId() {
        return topicId;
    }

    @Nullable
    public String getTopicName() {
        return topicName;
    }

    @Nullable
    public Integer getSectionNo() {
        return sectionNo;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    public void setCoverUrl(@Nullable String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public void setDurationSec(int durationSec) {
        this.durationSec = durationSec;
    }

    public void setPublishedAt(@Nullable String publishedAt) {
        this.publishedAt = publishedAt;
    }

    public void setCategoryName(@Nullable String categoryName) {
        this.categoryName = categoryName;
    }

    public void setLearnStatus(@Nullable String learnStatus) {
        this.learnStatus = learnStatus;
    }

    public void setPositionSec(int positionSec) {
        this.positionSec = positionSec;
    }

    public void setProgressRatio(@Nullable BigDecimal progressRatio) {
        this.progressRatio = progressRatio;
    }

    public void setTopicId(@Nullable Long topicId) {
        this.topicId = topicId;
    }

    public void setTopicName(@Nullable String topicName) {
        this.topicName = topicName;
    }

    public void setSectionNo(@Nullable Integer sectionNo) {
        this.sectionNo = sectionNo;
    }
}
