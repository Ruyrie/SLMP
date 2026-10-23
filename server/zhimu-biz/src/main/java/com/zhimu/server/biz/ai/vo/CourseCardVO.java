package com.zhimu.server.biz.ai.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程卡（API-K18、K17 courses、K19 related_courses）。
 */
@Data
public class CourseCardVO {

    private Long id;
    private String title;
    private String coverUrl;
    private int durationSec;
    private String publishedAt;
    private String categoryName;
    /** NOT_STARTED / LEARNING / COMPLETED */
    private String learnStatus;
    private int positionSec;
    private BigDecimal progressRatio;
    private Long topicId;
    private String topicName;
    private Integer sectionNo;
}
