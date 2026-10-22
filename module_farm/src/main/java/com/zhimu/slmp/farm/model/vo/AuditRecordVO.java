package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.Nullable;

/**
 * 审核记录：type 为 SUBMIT、RESUBMIT、REJECT、APPROVE；desc 重新提交为变更摘要，驳回为原因。
 */
public class AuditRecordVO {

    public static final String TYPE_SUBMIT = "SUBMIT";
    public static final String TYPE_RESUBMIT = "RESUBMIT";
    public static final String TYPE_REJECT = "REJECT";
    public static final String TYPE_APPROVE = "APPROVE";

    @Nullable
    private String type;
    @Nullable
    private String typeText;
    @Nullable
    private String time;
    @Nullable
    private String statusTag;
    @Nullable
    private String desc;
    private int submitNo;

    @Nullable
    public String getType() {
        return type;
    }

    @Nullable
    public String getTypeText() {
        return typeText;
    }

    @Nullable
    public String getTime() {
        return time;
    }

    @Nullable
    public String getStatusTag() {
        return statusTag;
    }

    @Nullable
    public String getDesc() {
        return desc;
    }

    public int getSubmitNo() {
        return submitNo;
    }
}
