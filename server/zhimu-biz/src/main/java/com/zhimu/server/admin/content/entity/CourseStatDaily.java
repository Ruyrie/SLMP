package com.zhimu.server.admin.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 课程每日统计（t_course_stat_daily）。00:30 汇总前一日，重跑时整行覆盖。
 */
@Getter
@Setter
@ToString
@TableName("t_course_stat_daily")
public class CourseStatDaily {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private LocalDate statDate;
    private Integer playCount;
    private Integer viewerCount;
    private Integer finishCount;
    private Integer watchSeconds;
    private Integer appPlayCount;
    private Integer webPlayCount;
    private Integer p720PlayCount;
    private Integer p480PlayCount;
    private Integer mobilePlayCount;
    private Integer askAiCount;
    private Integer knowledgeClickCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
