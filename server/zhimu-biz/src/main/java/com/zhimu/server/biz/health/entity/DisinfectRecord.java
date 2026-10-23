package com.zhimu.server.biz.health.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 消毒记录（t_disinfect_record）：计划任务 / 空栏消毒 / 紧急消毒。 */
@Getter
@Setter
@FarmScoped
@TableName("t_disinfect_record")
public class DisinfectRecord extends AuditEntity {

    private Long farmId;
    private Long penId;
    /** PLAN_TASK / EMPTY_PEN / EMERGENCY */
    private String recordType;
    private Long taskId;
    private String planConcentration;
    private LocalDateTime actualTime;
    private Long executorId;
    private Long drugId;
    private String disinfectantName;
    private Long drugLotId;
    private String lotNo;
    private BigDecimal usageQty;
    private String usageUnit;
    private String concentration;
    private Boolean concentrationConfirmed;
    private String methodCode;
    private Boolean backfill;
    private Boolean inBatchCost;
    private Boolean inExecRate;
    private BigDecimal unitPrice;
    private BigDecimal costAmount;
    private Long stockFlowId;
    private String clientRequestId;

    @TableLogic
    private Boolean deleted;

    @Version
    private Integer version;
}
