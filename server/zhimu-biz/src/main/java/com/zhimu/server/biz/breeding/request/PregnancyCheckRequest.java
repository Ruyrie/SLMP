package com.zhimu.server.biz.breeding.request;

import lombok.Data;

/** 登记妊检（API-B15）；check_method 不传时服务端默认 ULTRASOUND。 */
@Data
public class PregnancyCheckRequest {

    private Long animalId;
    private Integer animalVersion;
    private Long cycleId;
    private String checkDate;
    /** PREG_RESULT：POSITIVE / NEGATIVE */
    private String result;
    private String checkMethod;
}
