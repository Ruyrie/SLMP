package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-K10 语音转文字结果（直连百炼时由客户端组装）
 */
public class SpeechResultVO {

    private boolean recognized;
    private String text;
    private int durationMs;

    public boolean isRecognized() {
        return recognized;
    }

    @Nullable
    public String getText() {
        return text;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public void setRecognized(boolean recognized) {
        this.recognized = recognized;
    }

    public void setText(@Nullable String text) {
        this.text = text;
    }

    public void setDurationMs(int durationMs) {
        this.durationMs = durationMs;
    }
}
