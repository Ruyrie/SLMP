package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * API-K19 课程播放信息（K05/K06、K03 内嵌视频）：两档 HLS 播放列表、所属专题、进度与续播位置。
 */
public class CoursePlayVO {

    private long id;
    private String title;
    private String categoryName;
    private int durationSec;
    private String sourceLabel;
    private String updatedAt;
    private String coverUrl;
    private String shareUrl;
    private List<VideoSourceVO> sources;
    private Long knowledgeId;
    private Integer sectionNo;
    private Long nextCourseId;
    private Topic topic;
    private Progress progress;
    private List<CourseCardVO> relatedCourses;

    /** 所属专题 */
    public static class Topic {
        private long id;
        private String name;
        private int learnedCount;
        private int courseCount;

        public long getId() {
            return id;
        }

        @Nullable
        public String getName() {
            return name;
        }

        public int getLearnedCount() {
            return learnedCount;
        }

        public int getCourseCount() {
            return courseCount;
        }

        public void setLearnedCount(int learnedCount) {
            this.learnedCount = learnedCount;
        }
    }

    /** 本账号的进度 */
    public static class Progress {
        private int positionSec;
        private boolean completed;
        private int resumePositionSec;

        public int getPositionSec() {
            return positionSec;
        }

        public boolean isCompleted() {
            return completed;
        }

        /** 续播位置（已学完为 0，从头播放） */
        public int getResumePositionSec() {
            return resumePositionSec;
        }
    }

    public long getId() {
        return id;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getCategoryName() {
        return categoryName;
    }

    public int getDurationSec() {
        return durationSec;
    }

    @Nullable
    public String getSourceLabel() {
        return sourceLabel;
    }

    @Nullable
    public String getUpdatedAt() {
        return updatedAt;
    }

    @Nullable
    public String getCoverUrl() {
        return coverUrl;
    }

    @Nullable
    public String getShareUrl() {
        return shareUrl;
    }

    @NonNull
    public List<VideoSourceVO> getSources() {
        return sources == null ? Collections.emptyList() : sources;
    }

    @Nullable
    public Long getKnowledgeId() {
        return knowledgeId;
    }

    @Nullable
    public Integer getSectionNo() {
        return sectionNo;
    }

    @Nullable
    public Long getNextCourseId() {
        return nextCourseId;
    }

    @Nullable
    public Topic getTopic() {
        return topic;
    }

    @Nullable
    public Progress getProgress() {
        return progress;
    }

    @NonNull
    public List<CourseCardVO> getRelatedCourses() {
        return relatedCourses == null ? Collections.emptyList() : relatedCourses;
    }

    /** 指定清晰度的播放源；没有时取第一档 */
    @Nullable
    public VideoSourceVO sourceOf(@Nullable String quality) {
        List<VideoSourceVO> list = getSources();
        for (VideoSourceVO s : list) {
            if (s.getQuality() != null && s.getQuality().equals(quality)) {
                return s;
            }
        }
        return list.isEmpty() ? null : list.get(0);
    }
}
