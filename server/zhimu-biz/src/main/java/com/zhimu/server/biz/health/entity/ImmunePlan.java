package com.zhimu.server.biz.health.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 免疫计划项目（t_immune_plan）：按模板生成或手工添加，批次项与种畜栏舍项共用。 */
@Getter
@Setter
@FarmScoped
@TableName("t_immune_plan")
public class ImmunePlan extends AuditEntity {

    private Long farmId;
    /** BATCH / BREEDING_PEN */
    private String targetType;
    private Long batchId;
    private Long penId;
    /** TEMPLATE / MANUAL */
    private String source;
    private Boolean preEntry;
    private Long templateId;
    private Integer templateVersion;
    private Long templateItemId;
    private Long catalogId;
    private Long drugId;
    private String vaccineName;
    private String immuneRound;
    /** BY_DAY_AGE / BY_DATE */
    private String planMode;
    private Integer planAgeDay;
    private LocalDate originalPlanDate;
    private LocalDate planDate;
    private LocalTime dueTime;
    private BigDecimal dose;
    private String doseUnit;
    private String methodCode;
    /** IMMUNE_PLAN_STATUS */
    private String status;
    private Boolean postponed;
    private LocalDate postponeFromDate;
    private String postponeReason;
    private Long postponedBy;
    private LocalDateTime postponedAt;
    private Long recordId;
    private Boolean manualDone;
    private Long preEntryHandledBy;
    private LocalDateTime preEntryHandledAt;
    private LocalDateTime missedAt;
    private Boolean missedNotified;
    /** USER_DELETED / BATCH_ENDED / PEN_EMPTY */
    private String cancelType;
    private Long cancelledBy;
    private LocalDateTime cancelledAt;

    @Version
    private Integer version;
}
