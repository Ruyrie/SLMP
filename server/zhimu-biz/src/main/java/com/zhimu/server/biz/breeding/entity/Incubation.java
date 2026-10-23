package com.zhimu.server.biz.breeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 孵化记录（t_incubation，模块05 文档 2.3.7）：入孵、照蛋、出雏三步同一行。 */
@Getter
@Setter
@FarmScoped
@TableName("t_incubation")
public class Incubation extends AuditEntity {

    private Long farmId;
    private Long breedingBatchId;
    /** 入孵时种用批次所在栏舍（分布多个栏舍时取在栏数最多者） */
    private Long penId;
    private Long breedId;
    private Integer incubationDays;
    private LocalDate setDate;
    private Integer setCount;
    private LocalDate expectedHatchDate;
    private LocalDate candleDate;
    private Integer fertileCount;
    private Long candleBy;
    private LocalDateTime candleAt;
    private LocalDate hatchDate;
    private Integer hatchedCount;
    private Integer healthyCount;
    private Integer weakCount;
    private Boolean transferToBatch;
    private Long targetPenId;
    private Long offspringBreedId;
    private Boolean capacityConfirmed;
    private Long batchId;
    private Long hatchBy;
    private LocalDateTime hatchAt;
    /** INCUBATION_STATUS：SET / CANDLED / HATCHED */
    private String status;
    private LocalDateTime remindBeforeSentAt;
    private LocalDateTime remindDueSentAt;
    private String setRequestId;
    private String hatchRequestId;

    @Version
    private Integer version;

    private Long updatedBy;
}
