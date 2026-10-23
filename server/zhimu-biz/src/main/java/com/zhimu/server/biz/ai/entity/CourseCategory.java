package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 课程分类（t_course_category）。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_course_category")
public class CourseCategory extends BaseEntity {

    private String name;
    private Integer sortNo;
    private String status;
}
