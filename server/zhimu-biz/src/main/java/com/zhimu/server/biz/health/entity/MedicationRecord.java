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

/** 用药记录（t_medication_record）：每批次一行，按栏舍登记时同组多条。 */
@Getter
@Setter
@FarmScoped
@TableName("t_medication_record")
public class MedicationRecord extends AuditEntity {

    private Long farmId;
    private String groupNo;
    /** BATCH / PEN */
    private String targetType;
    private Long penId;
    private Long batchId;
    private Long catalogId;
    private Long drugId;
    private String drugName;
    private Long drugLotId;
    private String lotNo;
    private LocalDate lotExpireDate;
    private BigDecimal dose;
    private String doseUnit;
    private BigDecimal recommendedDose;
    private String routeCode;
    private Integer courseDays;
    private LocalDate firstDate;
    private LocalDate lastDate;
    private Integer withdrawalDays;
    private LocalDate withdrawalEndDate;
    private Boolean overDose;
    private String overDoseNote;
    private Boolean categoryMismatch;
    private String usageNote;
    private String reason;
    private BigDecimal estimatedQty;
    private BigDecimal usedQty;
    private String usedUnit;
    private Long reportId;
    private Long diagnosisId;
    private Long executorId;
    private BigDecimal unitPrice;
    private BigDecimal costAmount;
    private Long stockFlowId;
    private String clientRequestId;

    @TableLogic
    private Boolean deleted;

    @Version
    private Integer version;
}
