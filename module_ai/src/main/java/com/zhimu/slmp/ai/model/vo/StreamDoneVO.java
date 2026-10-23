package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SSE done：完整轮次
 */
public class StreamDoneVO {

    private String answerType;
    private String finishReason;
    private AiMessageVO message;

    @Nullable
    public String getAnswerType() {
        return answerType;
    }

    @Nullable
    public String getFinishReason() {
        return finishReason;
    }

    @Nullable
    public AiMessageVO getMessage() {
        return message;
    }

    public void setAnswerType(@Nullable String answerType) {
        this.answerType = answerType;
    }

    public void setFinishReason(@Nullable String finishReason) {
        this.finishReason = finishReason;
    }

    public void setMessage(@Nullable AiMessageVO message) {
        this.message = message;
    }
}
