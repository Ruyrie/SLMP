package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 专题（ADM-C36–C39）。 */
@Data
public class CourseTopicVO {

    private Long id;
    private String name;
    private Long categoryId;
    private String categoryName;
    private String intro;
    private Long coverFileId;
    private String coverUrl;
    private String status;
    private String statusText;
    private String offReason;
    private Integer sortNo;
    private Integer courseCount;
    private Integer totalDurationSec;
    private String durationText;
    private Integer version;
    private LocalDateTime updatedAt;
    private List<TopicCourseVO> courses = new ArrayList<>();

    @Data
    public static class TopicCourseVO {
        private Long courseId;
        private String title;
        private Integer sectionNo;
        private Integer durationSec;
        private String status;
    }
}
