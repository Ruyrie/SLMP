package com.zhimu.server.biz.health.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-D07 添加免疫项目。 */
@Data
public class ImmunePlanCreateRequest {

    private Long drugId;
    /** BY_DAY_AGE / BY_DATE */
    private String planMode;
    private Integer planAgeDay;
    private LocalDate planDate;
    private BigDecimal dose;
    private String methodCode;
    private String immuneRound;
    private String dueTime;
}
