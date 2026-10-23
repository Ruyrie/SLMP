package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.math.BigDecimal;

/** API-D20 可选兽药。 */
@Data
public class MedicationDrugOptionVO {

    private Long drugId;
    private Long catalogId;
    private String drugName;
    private String spec;
    private String stockUnit;
    private BigDecimal availableQty;
    private String routeCode;
    private String routeName;
    private BigDecimal recommendedDose;
    private String doseUnit;
    private Integer recommendedCourseDays;
    private Integer withdrawalDays;
    private Boolean applicable;
    private String hintText;
}
