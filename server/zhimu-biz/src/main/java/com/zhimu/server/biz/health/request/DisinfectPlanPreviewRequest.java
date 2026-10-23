package com.zhimu.server.biz.health.request;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** API-D30 任务数与提醒预估（纯计算）。 */
@Data
public class DisinfectPlanPreviewRequest {

    private List<Long> penIds = new ArrayList<>();
    private Integer cycleDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long planId;
}
