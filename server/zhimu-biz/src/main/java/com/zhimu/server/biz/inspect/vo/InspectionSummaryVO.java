package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

import java.math.BigDecimal;

/** API-I01 巡检统计与截止时间。scope：ALL_PENS / MY_PENS。 */
@Data
public class InspectionSummaryVO {

    private String farmName;
    private String date;
    private String deadlineTime;
    private String remindTime;
    private String scope;
    private int shouldCount;
    private int doneCount;
    private int pendingCount;
    private int abnormalCount;
    private int missedCount;
    private Integer responsiblePenCount;
    private Integer noOwnerPendingCount;
    private BigDecimal completionRate;
    private boolean deadlinePassed;
    private boolean allDone;
    private boolean canEditDeadline;
    private String firstAbnormalPenName;
    private Boolean firstAbnormalReported;
}
