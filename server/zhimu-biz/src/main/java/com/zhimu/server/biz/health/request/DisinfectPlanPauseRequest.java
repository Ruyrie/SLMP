package com.zhimu.server.biz.health.request;

import lombok.Data;

/** API-D33 暂停计划：reason 选填，不超过 30 字。 */
@Data
public class DisinfectPlanPauseRequest {

    private String reason;
    private Integer version;
}
