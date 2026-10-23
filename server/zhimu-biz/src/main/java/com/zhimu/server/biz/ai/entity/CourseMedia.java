package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 课程媒体（t_course_media）：一档清晰度的可直接播放地址。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_course_media")
public class CourseMedia extends BaseEntity {

    private Long courseId;
    private String mediaType;
    private String quality;
    private String url;
    private Long sizeBytes;
    private String status;
    private Integer sortNo;
}
