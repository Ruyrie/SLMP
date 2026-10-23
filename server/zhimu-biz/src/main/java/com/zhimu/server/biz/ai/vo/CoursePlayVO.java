package com.zhimu.server.biz.ai.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * API-K19 课程播放信息。
 */
@Data
public class CoursePlayVO {

    private Long id;
    private String title;
    private String categoryName;
    private int durationSec;
    private String sourceLabel;
    private String updatedAt;
    private String coverUrl;
    private String shareUrl;
    private List<VideoSourceVO> sources = new ArrayList<>();
    private Long knowledgeId;
    private Integer sectionNo;
    private Long nextCourseId;
    private Topic topic;
    private Progress progress;
    private List<CourseCardVO> relatedCourses = new ArrayList<>();

    @Data
    public static class Topic {
        private Long id;
        private String name;
        private int learnedCount;
        private int courseCount;
    }

    @Data
    public static class Progress {
        private int positionSec;
        private boolean completed;
        /** 续播位置（已学完为 0，从头播放） */
        private int resumePositionSec;
    }
}
