package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 养殖课堂课程（t_course）。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_course")
public class Course extends BaseEntity {

    private String title;
    private Long categoryId;
    private String categoryName;
    private Long topicId;
    private String topicName;
    private Integer sectionNo;
    private Long knowledgeId;
    private Integer durationSec;
    private String sourceLabel;
    private String coverUrl;
    private String shareUrl;
    private String status;
    private LocalDateTime publishedAt;
}
