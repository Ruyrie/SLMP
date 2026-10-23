package com.zhimu.server.biz.health.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-D16 修改接种记录。 */
@Data
public class ImmuneRecordUpdateRequest {

    private Long drugLotId;
    private BigDecimal dose;
    private Integer quantity;
    private String methodCode;
    private LocalDate vaccinateDate;
    private Long executorId;
    private Integer version;
}
