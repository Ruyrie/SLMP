package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.Nullable;

/**
 * API-I03 前 3 天超时、补录、漏巡（I01 养殖户版“前 3 天 · 超时与补录”）：issue_type 为 OVERDUE / BACKFILL / MISSED。
 */
public class RecentIssueVO {

    public static final String TYPE_OVERDUE = "OVERDUE";
    public static final String TYPE_BACKFILL = "BACKFILL";
    public static final String TYPE_MISSED = "MISSED";

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

    public long getInspectionId() { return inspectionId; }

    public long getPenId() { return penId; }

    public String getPenName() { return penName; }

    public String getInspectDate() { return inspectDate; }

    public String getIssueType() { return issueType; }

    public String getIssueTypeText() { return issueTypeText; }

    @Nullable
    public String getRegistrantName() { return registrantName; }

    @Nullable
    public String getRegisteredAt() { return registeredAt; }

    @Nullable
    public String getBackfillUntil() { return backfillUntil; }

    public boolean isCanBackfill() { return canBackfill; }
}
