package com.zhimu.server.biz.breeding.request;

import lombok.Data;

/** 登记照蛋（API-B30）。 */
@Data
public class CandlingRequest {

    private String candleDate;
    private Integer fertileCount;
    private Integer version;
}
