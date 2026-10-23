package com.zhimu.server.biz.health.request;

import lombok.Data;

import java.time.LocalDate;

/** API-D06 调整计划日期。 */
@Data
public class ImmunePlanPostponeRequest {

    private LocalDate newDate;
    private String reason;
    private Integer version;
}
