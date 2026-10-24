package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 课程专题（t_course_topic）。用户端 K07 读取；管理端 C03 维护节次与上下架。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_course_topic")
public class CourseTopic extends AuditEntity {

    private String name;
    private Long categoryId;
    private String categoryName;
    private String intro;
    private Long coverFileId;
    private String coverUrl;
    private Integer sortNo;
    private String status;
    private String offReason;
    private Long updatedBy;

    @Version
    private Integer version;
}
