package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 课程与知识分类（t_course_category，管理端文档称 t_content_category）。用户端分类筛选读取。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_course_category")
public class CourseCategory extends AuditEntity {

    private String name;
    private Integer sortNo;
    private String status;
    private Long updatedBy;

    @Version
    private Integer version;
}
