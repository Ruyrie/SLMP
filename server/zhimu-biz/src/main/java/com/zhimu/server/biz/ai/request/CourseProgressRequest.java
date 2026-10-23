package com.zhimu.server.biz.ai.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * API-K20 进度上报（event：START / TICK / PAUSE / SEEK / SWITCH / LEAVE / END）。
 */
@Getter
@Setter
public class CourseProgressRequest {

    private String playToken;
    private String event;
    private Integer positionSec;
    private Integer watchedSec;
    private String quality;
    private String networkType;
    private String entryType;
    /** yyyy-MM-dd HH:mm:ss（北京时间） */
    private LocalDateTime clientTime;
    private Boolean askAiClicked;
    private Boolean knowledgeClicked;
}
