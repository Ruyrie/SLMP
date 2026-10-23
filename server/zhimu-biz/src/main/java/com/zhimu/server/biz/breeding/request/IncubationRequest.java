package com.zhimu.server.biz.breeding.request;

import lombok.Data;

/** 登记入孵（API-B26）。 */
@Data
public class IncubationRequest {

    private Long breedingBatchId;
    private String setDate;
    private Integer setCount;
}
