package com.zhimu.server.biz.health.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-D14 登记接种 / 补免。 */
@Data
public class ImmuneRecordCreateRequest {

    private Long planId;
    private Long drugId;
    private Long drugLotId;
    private BigDecimal dose;
    private Integer quantity;
    private String methodCode;
    private LocalDate vaccinateDate;
    private Long executorId;
}
