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

/** 消毒任务（t_disinfect_task）：每栏舍每次一行，取消后 active_flag 置空。 */
@Getter
@Setter
@FarmScoped
@TableName("t_disinfect_task")
public class DisinfectTask extends AuditEntity {

    private Long farmId;
    private Long penId;
    private Long planId;
    /** PLAN / ADMIN_EMERGENCY */
    private String source;
    private LocalDate taskDate;
    private LocalTime dueTime;
    private Long catalogId;
    private String disinfectantName;
    private String concentration;
    private String methodCode;
    /** PENDING / OVERDUE / DONE / PAUSED / CANCELLED */
    private String status;
    private Boolean activeFlag;
    private Long recordId;
    private Boolean manualDone;
    private LocalDateTime overdueAt;
    private Boolean overdueNotified;
    private LocalDateTime cancelledAt;
    private String cancelReason;

    @Version
    private Integer version;
}
