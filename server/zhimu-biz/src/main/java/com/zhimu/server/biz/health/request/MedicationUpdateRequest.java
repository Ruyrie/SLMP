package com.zhimu.server.biz.health.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-D24 修改用药记录（用药对象和批次不可改）。 */
@Data
public class MedicationUpdateRequest {

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
    private Integer version;
}
