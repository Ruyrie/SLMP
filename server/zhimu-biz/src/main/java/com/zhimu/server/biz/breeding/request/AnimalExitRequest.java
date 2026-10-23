package com.zhimu.server.biz.breeding.request;

import lombok.Data;

/** 登记死亡 / 淘汰（API-B09）。 */
@Data
public class AnimalExitRequest {

    /** EXIT_TYPE：CULL / DEATH，默认 CULL */
    private String exitType;
    private String exitDate;
    private String reason;
    private Integer version;
}
