package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 饲喂标准阶段（t_feed_std_stage）：日龄连续不重叠，只有最后一段 end 为空。 */
@Getter
@Setter
@TableName("t_feed_std_stage")
public class FeedStdStage extends AdminDetailEntity {

    private Long versionId;
    private Integer sortNo;
    private String stageName;
    private String stageShortName;
    private Integer dayAgeStart;
    private Integer dayAgeEnd;
    private String feedTypeCode;
    private String suggestedFeedName;
    private BigDecimal dailyFeed;
    private Integer mealsPerDay;
    /** 逗号分隔的 HH:mm，例如 07:00,11:30,17:00 */
    private String mealTimes;
}
