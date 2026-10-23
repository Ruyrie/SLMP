package com.zhimu.server.biz.breeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 繁殖周期（t_breeding_cycle，模块05 文档 2.3.2）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_breeding_cycle")
public class BreedingCycle extends AuditEntity {

    private Long farmId;
    private Long animalId;
    private LocalDate firstMatingDate;
    private LocalDate lastMatingDate;
    private Integer matingCount;
    private Integer gestationDays;
    private Integer pregnancyCheckDays;
    private LocalDate checkDueDate;
    private LocalDate expectedDueDate;
    /** CYCLE_STATUS：MATED / PREGNANT / FAILED / FARROWED / WEANED */
    private String status;
    private Long pregCheckId;
    private Long farrowingId;
    private Long weaningId;
    private LocalDateTime remindBeforeSentAt;
    private LocalDateTime remindDueSentAt;
    private LocalDate endDate;
    /** CYCLE_END_REASON：NEGATIVE / WEANED / NO_LIVE / EXIT */
    private String endReason;

    @Version
    private Integer version;

    private Long updatedBy;
}
