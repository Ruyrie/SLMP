package com.zhimu.slmp.common.ui.change.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-U02 提交修改申请：biz_type、biz_id、change_type（UPDATE / VOID）、field_code、new_value、record_version、reason；
 * 从 U02“重新提交”进入时带 resubmit_of_id。
 */
public class ChangeRequestSubmitRequest {

    public static final String CHANGE_UPDATE = "UPDATE";
    public static final String CHANGE_VOID = "VOID";
    public static final String CHANGE_DELETE = "DELETE";

    private final String bizType;
    private final long bizId;
    private final String changeType;
    @Nullable
    private final String fieldCode;
    @Nullable
    private final String newValue;
    @Nullable
    private final String recordVersion;
    private final String reason;
    @Nullable
    private final Long resubmitOfId;

    public ChangeRequestSubmitRequest(@NonNull String bizType, long bizId, @NonNull String changeType,
                                      @Nullable String fieldCode, @Nullable String newValue,
                                      @Nullable String recordVersion, @NonNull String reason,
                                      @Nullable Long resubmitOfId) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.changeType = changeType;
        this.fieldCode = fieldCode;
        this.newValue = newValue;
        this.recordVersion = recordVersion;
        this.reason = reason;
        this.resubmitOfId = resubmitOfId;
    }

    public String getBizType() {
        return bizType;
    }

    public long getBizId() {
        return bizId;
    }

    public String getChangeType() {
        return changeType;
    }

    @Nullable
    public String getFieldCode() {
        return fieldCode;
    }

    @Nullable
    public String getNewValue() {
        return newValue;
    }

    @Nullable
    public String getRecordVersion() {
        return recordVersion;
    }

    public String getReason() {
        return reason;
    }

    @Nullable
    public Long getResubmitOfId() {
        return resubmitOfId;
    }
}
