package com.zhimu.server.biz.breeding.request;

import lombok.Data;

/** 修改未转批次的产仔（API-B18）。 */
@Data
public class FarrowingUpdateRequest {

    private String farrowDate;
    private Integer totalBorn;
    private Integer liveBorn;
    private Integer stillborn;
    private Integer version;
}
