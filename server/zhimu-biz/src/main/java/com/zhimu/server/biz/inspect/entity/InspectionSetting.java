package com.zhimu.server.biz.inspect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/** 巡检设置（t_inspection_setting）：每场一行，没有行时按 18:00 处理。 */
@Getter
@Setter
@FarmScoped
@TableName("t_inspection_setting")
public class InspectionSetting extends AuditEntity {

    private Long farmId;
    private LocalTime deadlineTime;
    /** 截止前提醒分钟数，固定 60 */
    private Integer remindBeforeMin;
    private Long updatedBy;

    @Version
    private Integer version;
}
