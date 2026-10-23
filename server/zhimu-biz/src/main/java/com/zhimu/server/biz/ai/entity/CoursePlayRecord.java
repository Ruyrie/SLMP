package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 课程播放记录（t_course_play_record）：一次播放一行，按 (user_id, play_token) 去重。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_course_play_record")
public class CoursePlayRecord extends BaseEntity {

    private Long userId;
    private Long courseId;
    private String playToken;
    private String entryType;
    private String clientType;
    private String networkType;
    private String quality;
    private Integer startPositionSec;
    private Integer endPositionSec;
    private Integer maxPositionSec;
    private Integer watchedSec;
    private Integer askAiClicked;
    private Integer knowledgeClicked;
    private LocalDateTime startedAt;
    private LocalDateTime lastReportAt;
    private LocalDateTime lastClientTime;
    private LocalDateTime endedAt;
}
