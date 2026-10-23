package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 课程专题（t_course_topic）。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_course_topic")
public class CourseTopic extends BaseEntity {

    private String name;
    private Long categoryId;
    private String categoryName;
    private String intro;
    private String coverUrl;
    private Integer sortNo;
    private String status;
}
