package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.Nullable;

/**
 * 提交结果查询（API-B32）：超时后确认记录是否已保存。
 */
public class SubmissionVO {

    private boolean saved;
    /** SUBMISSION_TYPE */
    @Nullable
    private String type;
    @Nullable
    private Long recordId;
    @Nullable
    private RegisterResultVO result;

    public boolean isSaved() {
        return saved;
    }

    @Nullable
    public String getType() {
        return type;
    }

    @Nullable
    public Long getRecordId() {
        return recordId;
    }

    @Nullable
    public RegisterResultVO getResult() {
        return result;
    }
}
