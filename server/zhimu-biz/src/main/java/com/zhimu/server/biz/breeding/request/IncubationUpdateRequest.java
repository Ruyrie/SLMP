package com.zhimu.server.biz.breeding.request;

import lombok.Data;

/** 修改入孵 / 照蛋 / 未转批次的出雏（API-B29）。 */
@Data
public class IncubationUpdateRequest {

    private String setDate;
    private Integer setCount;
    private String candleDate;
    private String hatchDate;
    private Integer fertileCount;
    private Integer hatchedCount;
    private Integer healthyCount;
    private Integer version;
}
