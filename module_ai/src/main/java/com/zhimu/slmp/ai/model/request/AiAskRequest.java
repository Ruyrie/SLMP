package com.zhimu.slmp.ai.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-K06 提问（session_id 新会话不传；question_text 与 image_file_ids 至少一项）
 */
public class AiAskRequest {

    private Long sessionId;
    private String questionText;
    private List<Long> imageFileIds;
    private Long batchId;
    private Long contextCourseId;

    @Nullable
    public Long getSessionId() {
        return sessionId;
    }

    @Nullable
    public String getQuestionText() {
        return questionText;
    }

    @NonNull
    public List<Long> getImageFileIds() {
        return imageFileIds == null ? Collections.emptyList() : imageFileIds;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public Long getContextCourseId() {
        return contextCourseId;
    }

    public void setSessionId(@Nullable Long sessionId) {
        this.sessionId = sessionId;
    }

    public void setQuestionText(@Nullable String questionText) {
        this.questionText = questionText;
    }

    public void setImageFileIds(@Nullable List<Long> imageFileIds) {
        this.imageFileIds = imageFileIds;
    }

    public void setBatchId(@Nullable Long batchId) {
        this.batchId = batchId;
    }

    public void setContextCourseId(@Nullable Long contextCourseId) {
        this.contextCourseId = contextCourseId;
    }
}
