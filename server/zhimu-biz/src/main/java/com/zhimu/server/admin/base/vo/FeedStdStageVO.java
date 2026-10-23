package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 饲喂标准阶段（ADM-D07 feed，ADM-D11–D13 复用）。 */
@Data
public class FeedStdStageVO {

    private Long stageId;
    private Integer sortNo;
    private String stageName;
    private String stageShortName;
    private Integer dayAgeStart;
    private Integer dayAgeEnd;
    private String feedTypeCode;
    private String feedTypeText;
    private String suggestedFeedName;
    private BigDecimal dailyFeed;
    private Integer mealsPerDay;
    /** 拆开后的时刻数组，例如 ["07:00","11:30","17:00"] */
    private List<String> mealTimes = new ArrayList<>();
    /** 草稿阶段：与线上阶段不同 */
    private Boolean changed;
}
