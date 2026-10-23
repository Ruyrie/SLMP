package com.zhimu.server.biz.health.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 消毒计划（t_disinfect_plan）：不提供删除，不设 deleted。 */
@Getter
@Setter
@FarmScoped
@TableName("t_disinfect_plan")
public class DisinfectPlan extends AuditEntity {

    private Long farmId;
    private Integer cycleDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime dueTime;
    private Long catalogId;
    private Long drugId;
    private String disinfectantName;
    private String concentration;
    private String methodCode;
    /** ACTIVE / PAUSED / ENDED */
    private String status;
    private String pauseReason;
    private Long pausedBy;
    private LocalDateTime pausedAt;
    private LocalDateTime resumedAt;
    private LocalDate generatedUntil;

    @Version
    private Integer version;
}
