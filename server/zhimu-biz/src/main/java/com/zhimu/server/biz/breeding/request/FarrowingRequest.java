package com.zhimu.server.biz.breeding.request;

import lombok.Data;

/** 登记产仔（API-B17）。 */
@Data
public class FarrowingRequest {

    private Long animalId;
    private Integer animalVersion;
    private String farrowDate;
    private Integer totalBorn;
    private Integer liveBorn;
    private Integer stillborn;
    private Boolean transferToBatch;
    private Long targetPenId;
    private Long offspringBreedId;
    private Boolean capacityConfirmed;
}
