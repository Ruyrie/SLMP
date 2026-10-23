package com.zhimu.server.biz.inspect.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** API-I11 栏舍巡检历史的一行。 */
@Data
public class PenInspectionRowVO {

    private long inspectionId;
    private String inspectDate;
    private Boolean isToday;
    private String inspectStatus;
    private List<String> tags;
    private String abnormalSource;
    private String abnormalSummary;
    private String manualReason;
    private String registrantName;
    private String registeredAt;
    private boolean canModify;
    private String modifyTarget;
    private boolean canChangeRequest;
    private Long pendingRequestId;
    private boolean hasRevisions;
    private boolean canBackfill;
    private String backfillUntil;
    @JsonProperty("backfill_expired")
    private Boolean isBackfillExpired;
    private String resultSummary;
}
