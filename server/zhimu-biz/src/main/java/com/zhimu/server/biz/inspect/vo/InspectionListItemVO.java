package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

import java.util.List;

/** API-I02 某日栏舍清单的一行。display_status：ABNORMAL / PENDING / DONE / MISSED。 */
@Data
public class InspectionListItemVO {

    private long inspectionId;
    private long penId;
    private String penCode;
    private String penName;
    private Long batchId;
    private List<String> batchNos;
    private int stockCount;
    private List<String> responsibleNames;
    private String displayStatus;
    private String displayStatusText;
    private String abnormalSource;
    private String abnormalSummary;
    private String manualReason;
    private String registrantName;
    private String registeredAt;
    private String locationStatus;
    private Integer distanceM;
    private Boolean isOverdue;
    private Boolean isBackfill;
    private boolean deadlinePassed;
    private Long reportId;
    private String reportStatus;
    private boolean canRegister;
    private boolean canModify;
    private boolean canReport;
    private boolean canViewDiagnosis;
    private int version;
}
