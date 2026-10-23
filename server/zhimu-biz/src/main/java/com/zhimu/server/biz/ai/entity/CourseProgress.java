package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程学习进度（t_course_progress）：账号 × 课程，不带 farm_id。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_course_progress")
public class CourseProgress extends BaseEntity {

    private Long userId;
    private Long courseId;
    private Integer positionSec;
    private Integer maxPositionSec;
    private Integer watchedSec;
    private Integer durationSec;
    private BigDecimal progressRatio;
    private Integer completed;
    private LocalDateTime completedAt;
    private LocalDateTime lastWatchedAt;
    private String lastClientType;
    private String lastQuality;
    private LocalDateTime lastClientTime;
}
