package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * API-I11 栏舍巡检历史的一行（I04）：tags 为 ABNORMAL、OVERDUE、BACKFILL、ON_SITE、OFF_SITE、NO_LOCATION、MISSED、
 * PENDING_REVIEW；modify_target 为 REGISTER（打开 I02）/ BACKFILL（打开 I03 并锁定日期）。
 */
public class PenInspectionRowVO {

    public static final String TAG_ABNORMAL = "ABNORMAL";
    public static final String TAG_OVERDUE = "OVERDUE";
    public static final String TAG_BACKFILL = "BACKFILL";
    public static final String TAG_ON_SITE = "ON_SITE";
    public static final String TAG_OFF_SITE = "OFF_SITE";
    public static final String TAG_NO_LOCATION = "NO_LOCATION";
    public static final String TAG_MISSED = "MISSED";
    public static final String TAG_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String TARGET_REGISTER = "REGISTER";
    public static final String TARGET_BACKFILL = "BACKFILL";

    private long inspectionId;
    private String inspectDate;
    private boolean isToday;
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
    private boolean backfillExpired;
    /** 摘要：“正常”或异常项，例如“粪便 水样”（客户端补充，服务端可不返回） */
    private String resultSummary;

    public long getInspectionId() { return inspectionId; }

    public String getInspectDate() { return inspectDate; }

    public boolean isToday() { return isToday; }

    public String getInspectStatus() { return inspectStatus; }

    @NonNull
    public List<String> getTags() { return tags == null ? Collections.emptyList() : tags; }

    @Nullable
    public String getAbnormalSource() { return abnormalSource; }

    @Nullable
    public String getAbnormalSummary() { return abnormalSummary; }

    @Nullable
    public String getManualReason() { return manualReason; }

    @Nullable
    public String getRegistrantName() { return registrantName; }

    @Nullable
    public String getRegisteredAt() { return registeredAt; }

    public boolean isCanModify() { return canModify; }

    @Nullable
    public String getModifyTarget() { return modifyTarget; }

    public boolean isCanChangeRequest() { return canChangeRequest; }

    @Nullable
    public Long getPendingRequestId() { return pendingRequestId; }

    public boolean isHasRevisions() { return hasRevisions; }

    public boolean isCanBackfill() { return canBackfill; }

    @Nullable
    public String getBackfillUntil() { return backfillUntil; }

    public boolean isBackfillExpired() { return backfillExpired; }

    @Nullable
    public String getResultSummary() { return resultSummary; }

    public boolean isMissed() { return "MISSED".equals(inspectStatus); }

    public boolean hasTag(@NonNull String tag) { return getTags().contains(tag); }
}
