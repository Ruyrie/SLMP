package com.zhimu.server.biz.health.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-D21 预估可出栏日期与预计用量（纯计算）。 */
@Data
public class MedicationPreviewRequest {

    private String targetType;
    private Long batchId;
    private Long penId;
    private Long drugId;
    private LocalDate firstDate;
    private Integer courseDays;
    private BigDecimal dose;
    private Long excludeRecordId;
}
