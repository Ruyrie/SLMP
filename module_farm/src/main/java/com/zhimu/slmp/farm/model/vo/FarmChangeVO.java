package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 资料变更（API-F13 最近一次、API-F14 提交结果）。changed_fields 为 NAME、ADDRESS、QUALIFICATION。
 */
public class FarmChangeVO {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REJECTED = "REJECTED";

    private long changeId;
    private int changeNo;
    private int addedCount;
    private int removedCount;
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    @Nullable
    private String submittedAt;
    @Nullable
    private String auditedAt;
    @Nullable
    private String rejectReason;
    @Nullable
    private String remark;
    @Nullable
    private List<String> changedFields;
    @Nullable
    private ChangeSnapshotVO before;
    @Nullable
    private ChangeSnapshotVO after;

    public long getChangeId() {
        return changeId;
    }

    public int getChangeNo() {
        return changeNo;
    }

    public int getAddedCount() {
        return addedCount;
    }

    public int getRemovedCount() {
        return removedCount;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }

    @Nullable
    public String getSubmittedAt() {
        return submittedAt;
    }

    @Nullable
    public String getAuditedAt() {
        return auditedAt;
    }

    @Nullable
    public String getRejectReason() {
        return rejectReason;
    }

    @Nullable
    public String getRemark() {
        return remark;
    }

    @NonNull
    public List<String> getChangedFields() {
        return changedFields == null ? Collections.emptyList() : changedFields;
    }

    @Nullable
    public ChangeSnapshotVO getBefore() {
        return before;
    }

    @Nullable
    public ChangeSnapshotVO getAfter() {
        return after;
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }
}
