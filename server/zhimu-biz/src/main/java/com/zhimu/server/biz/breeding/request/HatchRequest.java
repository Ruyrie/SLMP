package com.zhimu.server.biz.breeding.request;

import lombok.Data;

/** 登记出雏（API-B31）。 */
@Data
public class HatchRequest {

    private String hatchDate;
    private Integer hatchedCount;
    private Integer healthyCount;
    private Boolean transferToBatch;
    private Long targetPenId;
    private Long offspringBreedId;
    private Boolean capacityConfirmed;
    private Integer version;
}
