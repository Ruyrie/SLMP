package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.time.LocalDate;

/** API-D11 登记接种的计划下拉项。 */
@Data
public class ImmunePlanOptionVO {

    private Long planId;
    /** BATCH / BREEDING_PEN */
    private String targetType;
    private Long batchId;
    private String batchNo;
    private String penName;
    private String vaccineName;
    private String immuneRound;
    private Integer currentCount;
    private LocalDate planDate;
    /** 今天 / 10月12日 */
    private String planDateText;
    private String status;
    private String statusText;
}
