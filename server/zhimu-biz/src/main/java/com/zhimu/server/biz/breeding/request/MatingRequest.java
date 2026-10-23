package com.zhimu.server.biz.breeding.request;

import lombok.Data;

/** 登记配种（API-B12）。 */
@Data
public class MatingRequest {

    private Long animalId;
    private Integer animalVersion;
    private String matingDate;
    /** MATING_METHOD：ARTIFICIAL / NATURAL */
    private String matingMethod;
    private String boarInfo;
}
