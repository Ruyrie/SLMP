package com.zhimu.server.biz.breeding.request;

import lombok.Data;

/** 修改妊检（API-B16）。 */
@Data
public class PregnancyCheckUpdateRequest {

    private String checkDate;
    private String result;
    private String checkMethod;
    private Integer version;
}
