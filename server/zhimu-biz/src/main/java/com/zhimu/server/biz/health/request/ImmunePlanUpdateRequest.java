package com.zhimu.server.biz.health.request;

import lombok.Data;

import java.math.BigDecimal;

/** API-D08 修改剂量、方式、截止时刻（至少一项）。 */
@Data
public class ImmunePlanUpdateRequest {

    private BigDecimal dose;
    private String methodCode;
    private String dueTime;
    private Integer version;
}
