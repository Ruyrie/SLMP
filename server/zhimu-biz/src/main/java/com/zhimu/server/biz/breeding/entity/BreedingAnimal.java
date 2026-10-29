package com.zhimu.server.biz.breeding.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
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
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long currentCycleId;
    private LocalDate lastWeanDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate lastCycleEndDate;
    /** EXIT_TYPE：CULL / DEATH */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String exitType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate exitDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String exitReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String statusBeforeExit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long exitBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime exitAt;

    @Version
    private Integer version;

    private Long updatedBy;
}
