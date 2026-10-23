package com.zhimu.server.biz.health.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-D22 登记用药。 */
@Data
public class MedicationCreateRequest {

    /** BATCH / PEN */
    private String targetType;
    private Long batchId;
    private Long penId;
    private Long drugId;
    private Long drugLotId;
    private BigDecimal dose;
    private BigDecimal usedQty;
    private Integer courseDays;
    private LocalDate firstDate;
    private String overDoseNote;
    private String usageNote;
    private String reason;
    private Long executorId;
    private Long reportId;
    private Long diagnosisId;
}
