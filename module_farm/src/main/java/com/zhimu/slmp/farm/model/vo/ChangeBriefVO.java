package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.Nullable;

/**
 * 资料变更摘要（FarmDetailVO 的 pending_change、rejected_change；客户端暂定类名）。
 */
public class ChangeBriefVO {

    private long changeId;
    @Nullable
    private String submittedAt;
    @Nullable
    private String rejectReason;
    @Nullable
    private String auditedAt;

    public long getChangeId() {
        return changeId;
    }

    @Nullable
    public String getSubmittedAt() {
        return submittedAt;
    }

    @Nullable
    public String getRejectReason() {
        return rejectReason;
    }

    @Nullable
    public String getAuditedAt() {
        return auditedAt;
    }
}
