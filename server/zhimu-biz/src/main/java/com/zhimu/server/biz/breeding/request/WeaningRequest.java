package com.zhimu.server.biz.breeding.request;

import lombok.Data;

import java.math.BigDecimal;

/** 登记断奶（API-B20）。 */
@Data
public class WeaningRequest {

    private Long animalId;
    private Integer animalVersion;
    private String weanDate;
    private Integer weanedCount;
    private BigDecimal litterWeight;
}
