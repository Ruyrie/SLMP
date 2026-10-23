package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.time.LocalDate;

/** API-D17 每个药品的休药标签。 */
@Data
public class WithdrawalDrugVO {

    private Long catalogId;
    private String drugName;
    private LocalDate lastDate;
    private Integer withdrawalDays;
    private LocalDate endDate;
    /** LATEST / ENDED / NONE */
    private String tag;
    private String tagText;
}
