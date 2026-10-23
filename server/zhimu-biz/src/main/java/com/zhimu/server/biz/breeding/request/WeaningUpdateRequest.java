package com.zhimu.server.biz.breeding.request;

import lombok.Data;

import java.math.BigDecimal;

/** 修改断奶（API-B21）。 */
@Data
public class WeaningUpdateRequest {

    private String weanDate;
    private Integer weanedCount;
    private BigDecimal litterWeight;
    private Integer version;
}
