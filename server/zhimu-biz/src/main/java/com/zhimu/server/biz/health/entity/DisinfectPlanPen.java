package com.zhimu.server.biz.health.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

/** 消毒计划覆盖栏舍（t_disinfect_plan_pen）：修改计划时整体替换。 */
@Getter
@Setter
@FarmScoped
@TableName("t_disinfect_plan_pen")
public class DisinfectPlanPen extends AuditEntity {

    private Long farmId;
    private Long planId;
    private Long penId;
}
