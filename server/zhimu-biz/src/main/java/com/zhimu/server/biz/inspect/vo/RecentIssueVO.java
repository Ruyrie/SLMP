package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

/** API-I03 前 3 天超时、补录、漏巡。issue_type：OVERDUE / BACKFILL / MISSED。 */
@Data
public class RecentIssueVO {

    private long inspectionId;
    private long penId;
    private String penName;
    private String inspectDate;
    private String issueType;
    private String issueTypeText;
    private String registrantName;
    private String registeredAt;
    private String backfillUntil;
    private boolean canBackfill;
}
