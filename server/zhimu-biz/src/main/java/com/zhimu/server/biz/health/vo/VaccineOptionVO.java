package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.math.BigDecimal;

/** API-D13 本场疫苗档案下拉项（含无库存，禁用药不列）。 */
@Data
public class VaccineOptionVO {

    private Long drugId;
    private Long catalogId;
    private String name;
    private String doseUnit;
    private String stockUnit;
    private BigDecimal availableQty;
    private Boolean noStock;
    private String defaultMethodCode;
}
