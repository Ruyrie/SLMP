package com.zhimu.server.biz.breeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 种畜档案（t_breeding_animal，模块05 文档 2.3.1）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_breeding_animal")
public class BreedingAnimal extends AuditEntity {

    private Long farmId;
    private String earTag;
    private Long breedId;
    private LocalDate birthDate;
    /** ANIMAL_SOURCE：PURCHASED / SELF_BRED */
    private String source;
    private Long penId;
    private LocalDate filingDate;
    private Integer initialParity;
    private Integer parity;
    /** BREEDING_STATUS：EMPTY / MATED / PREGNANT / LACTATING / CULLED / DEAD */
    private String status;
    private Long currentCycleId;
    private LocalDate lastWeanDate;
    private LocalDate lastCycleEndDate;
    /** EXIT_TYPE：CULL / DEATH */
    private String exitType;
    private LocalDate exitDate;
    private String exitReason;
    private String statusBeforeExit;
    private Long exitBy;
    private LocalDateTime exitAt;

    @Version
    private Integer version;

    private Long updatedBy;
}
