package com.zhimu.server.biz.feeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/** 饲喂方案阶段餐次（t_feed_plan_meal）：保存方案时按阶段整组替换。 */
@Getter
@Setter
@FarmScoped
@TableName("t_feed_plan_meal")
public class FeedPlanMeal extends BaseEntity {

    private Long farmId;
    private Long planId;
    private Long stageId;
    private Integer mealNo;
    private String mealCode;
    private String mealName;
    private LocalTime feedTime;
}
