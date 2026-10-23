package com.zhimu.server.biz.inspect.vo;

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
    /**
     * 补录期限已过。App 的字段是 backfillExpired（JSON backfill_expired，见 mock history_2003.json）；
     * 命名成 isBackfillExpired 会被 Jackson 输出成 is_backfill_expired，与 App 契约不一致。
     */
    private Boolean backfillExpired;
    private String resultSummary;
}
