package com.zhimu.server.biz.feeding.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 方案阶段（FeedPlanSaveRequest.stages）。 */
@Data
public class PlanStage {
    private Long stageId;
    private Long stdStageId;
    private String stageName;
    private Integer dayAgeStart;
    private Integer dayAgeEnd;
    private Long feedId;
    private BigDecimal dailyQtyPerHead;
    private Integer mealsPerDay;
    private List<String> mealTimes;
}
