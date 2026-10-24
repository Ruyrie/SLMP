package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 目录按畜种用量与休药期（兽药、疫苗四个畜种各一行；消毒剂为空）。 */
@Getter
@Setter
public class DrugUsageVO {

    private String species;
    private String speciesText;
    private Boolean applicable;
    private String adminRoute;
    private String adminRouteText;
    private BigDecimal recommendedDose;
    private String doseUnit;
    private String doseUnitText;
    private Integer courseDays;
    private Integer withdrawalDays;
}
