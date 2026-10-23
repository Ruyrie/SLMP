package com.zhimu.server.biz.breeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 产仔记录（t_farrowing_record，模块05 文档 2.3.5）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_farrowing_record")
public class FarrowingRecord extends AuditEntity {

    private Long farmId;
    private Long animalId;
    private Long cycleId;
    private Integer parityNo;
    private LocalDate farrowDate;
    private Integer totalBorn;
    private Integer liveBorn;
    private Integer stillborn;
    private Boolean transferToBatch;
    private Long targetPenId;
    private Long offspringBreedId;
    private Boolean capacityConfirmed;
    private Long batchId;
    private String clientRequestId;

    @Version
    private Integer version;

    private Long updatedBy;
}
