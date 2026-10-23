package com.zhimu.server.biz.health.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhimu.server.common.annotation.ManagerView;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** API-D02 免疫计划列表项（D04、D06–D08 返回）。 */
@Data
public class ImmunePlanVO {

    private Long id;
    private Integer version;
    private String vaccineName;
    private String immuneRound;
    /** BY_DAY_AGE / BY_DATE */
    private String planMode;
    private LocalDate planDate;
    @JsonProperty("is_today")
    private Boolean isToday;
    private String dueTime;
    private Boolean overdue;
    private Integer ageAtPlan;
    private Boolean postponed;
    private LocalDate postponeFromDate;
    private String postponedByName;
    private String responsibleText;
    /** 提醒说明；员工不输出 */
    @ManagerView
    private String remindText;
    private String status;
    private String statusText;
    private Long recordId;
    private LocalDate recordDate;
    private String recordUserName;
    private Integer recordQuantity;
    private Boolean backfill;
    private Boolean manualDone;
    private String cancelType;
    private String cancelledByName;
    private BigDecimal dose;
    private String doseUnit;
    private String methodCode;
    private String methodName;
    private List<String> actions = new ArrayList<>();
}
