package com.zhimu.server.biz.health.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 接种记录（t_immune_record）：同一计划最多一条有效记录，作废置 deleted = 1。 */
@Getter
@Setter
@FarmScoped
@TableName("t_immune_record")
public class ImmuneRecord extends AuditEntity {

    private Long farmId;
    private Long batchId;
    private Long penId;
    private Long planId;
    private Long drugId;
    private String vaccineName;
    private Long drugLotId;
    private String lotNo;
    private LocalDate lotExpireDate;
    private BigDecimal dose;
    private String doseUnit;
    private Integer quantity;
    private BigDecimal totalDose;
    private String methodCode;
    private LocalDate vaccinateDate;
    private Long executorId;
    private Boolean backfill;
    private BigDecimal unitPrice;
    private BigDecimal costAmount;
    private Long stockFlowId;
    private String clientRequestId;

    @TableLogic
    private Boolean deleted;

    @Version
    private Integer version;
}
