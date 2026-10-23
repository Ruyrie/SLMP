package com.zhimu.server.biz.feeding.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 饲喂方案生长阶段（t_feed_plan_stage）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_feed_plan_stage")
public class FeedPlanStage extends AuditEntity {

    private Long farmId;
    private Long planId;
    private Long batchId;
    private Integer sortNo;
    private String stageName;
    private Integer dayAgeStart;
    private Integer dayAgeEnd;
    private Long feedId;
    private BigDecimal dailyQtyPerHead;
    private Integer mealsPerDay;
    private Long stdStageId;
    private Long stdFeedId;
    private String stdFeedName;
    private BigDecimal stdDailyQty;
    private Integer stdMealsPerDay;
    private String stdMealTimes;

    @TableLogic
    private Boolean deleted;
}
