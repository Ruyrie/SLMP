package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

/** API-I07 / I08 / I09 / I25 登记、修改、补录的结果。 */
@Data
public class InspectionSubmitResultVO {

    private long inspectionId;
    private int version;
    private String penName;
    private String inspectDate;
    private Boolean isAbnormal;
    private Boolean isOverdue;
    private Boolean isBackfill;
    private boolean todoCompleted;
    private String locationStatus;
    private Integer distanceM;
}
