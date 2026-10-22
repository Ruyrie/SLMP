package com.zhimu.slmp.common.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * U02 我的修改申请列表与详情（API-U03、U04）。
 */
public class ChangeRequestVO {

    private long id;
    @Nullable
    private String requestNo;
    @Nullable
    private String bizType;
    @Nullable
    private String bizTypeText;
    @Nullable
    private String recordLocator;
    /** UPDATE / DELETE / VOID */
    @Nullable
    private String changeType;
    @Nullable
    private String fieldName;
    @Nullable
    private String beforeDisplay;
    @Nullable
    private String afterDisplay;
    @Nullable
    private String reason;
    /** 影响范围文字 */
    @Nullable
    private List<String> impacts;
    @Nullable
    private List<String> recalcTags;
    /** PENDING / APPROVED / REJECTED / CANCELED */
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    @Nullable
    private UserBriefVO submittedBy;
    @Nullable
    private String submittedAt;
    @Nullable
    private String reviewedAt;
    @Nullable
    private String reviewComment;
    @Nullable
    private String rejectReason;

    public long getId() {
        return id;
    }

    @Nullable
    public String getRequestNo() {
        return requestNo;
    }

    @Nullable
    public String getBizType() {
        return bizType;
    }

    @Nullable
    public String getBizTypeText() {
        return bizTypeText;
    }

    @Nullable
    public String getRecordLocator() {
        return recordLocator;
    }

    @Nullable
    public String getChangeType() {
        return changeType;
    }

    @Nullable
    public String getFieldName() {
        return fieldName;
    }

    @Nullable
    public String getBeforeDisplay() {
        return beforeDisplay;
    }

    @Nullable
    public String getAfterDisplay() {
        return afterDisplay;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    @NonNull
    public List<String> getImpacts() {
        return impacts == null ? Collections.emptyList() : impacts;
    }

    @NonNull
    public List<String> getRecalcTags() {
        return recalcTags == null ? Collections.emptyList() : recalcTags;
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
    public UserBriefVO getSubmittedBy() {
        return submittedBy;
    }

    @Nullable
    public String getSubmittedAt() {
        return submittedAt;
    }

    @Nullable
    public String getReviewedAt() {
        return reviewedAt;
    }

    @Nullable
    public String getReviewComment() {
        return reviewComment;
    }

    @Nullable
    public String getRejectReason() {
        return rejectReason;
    }
}
