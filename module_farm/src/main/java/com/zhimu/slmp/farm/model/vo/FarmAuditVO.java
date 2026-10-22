package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 审核结果（API-F05 响应、API-F06）。已通过的养殖场只返回 farm_id、name、status、status_text。
 */
public class FarmAuditVO {

    private long farmId;
    @Nullable
    private String name;
    @Nullable
    private String statusText;
    /** PENDING / REJECTED / NORMAL …（FarmStatus） */
    @Nullable
    private String status;
    @Nullable
    private String auditStatus;
    @Nullable
    private String firstSubmittedAt;
    @Nullable
    private String submittedAt;
    @Nullable
    private String restrictTip;
    @Nullable
    private Integer submitNo;
    @Nullable
    private Integer auditVersion;
    @Nullable
    private Integer reshootCount;
    @Nullable
    private String auditedAt;
    @Nullable
    private String rejectReason;
    @Nullable
    private RegistrationVO registration;
    @Nullable
    private List<AuditRecordVO> records;

    public long getFarmId() {
        return farmId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getAuditStatus() {
        return auditStatus;
    }

    @Nullable
    public String getFirstSubmittedAt() {
        return firstSubmittedAt;
    }

    @Nullable
    public String getSubmittedAt() {
        return submittedAt;
    }

    @Nullable
    public String getRestrictTip() {
        return restrictTip;
    }

    @Nullable
    public Integer getSubmitNo() {
        return submitNo;
    }

    @Nullable
    public Integer getAuditVersion() {
        return auditVersion;
    }

    @Nullable
    public Integer getReshootCount() {
        return reshootCount;
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
    public RegistrationVO getRegistration() {
        return registration;
    }

    /** 审核记录，倒序 */
    @NonNull
    public List<AuditRecordVO> getRecords() {
        return records == null ? Collections.emptyList() : records;
    }
}
