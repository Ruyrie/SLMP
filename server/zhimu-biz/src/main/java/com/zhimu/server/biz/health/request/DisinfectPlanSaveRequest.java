package com.zhimu.server.biz.health.request;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** API-D31 新建、API-D32 修改消毒计划。 */
@Data
public class DisinfectPlanSaveRequest {

    private List<Long> penIds = new ArrayList<>();
    private Integer cycleDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long catalogId;
    private String concentration;
    private String methodCode;
    private String dueTime;
    private Integer version;
}
