package com.zhimu.server.biz.breeding.vo;

import lombok.Getter;
import lombok.Setter;

/** 登记结果（API-B12、B15、B17、B20、B26、B31 及 B32 的 result）。 */
@Getter
@Setter
public class RegisterResultVO {

    private String recordType;
    private long recordId;
    private Long animalId;
    private String earTag;
    private String animalStatus;
    private String animalStatusText;
    private Long cycleId;
    private Boolean isFirst;
    private String expectedDueDate;
    private Long incubationId;
    private String expectedHatchDate;
    private Long batchId;
    private String batchNo;
    private Boolean todoCompleted;
}
