package com.zhimu.slmp.common.ai;

import androidx.annotation.NonNull;

/**
 * 语音识别结果：识别出的文字（已去首尾空白，没听清时为空串）、服务商与耗时。
 */
public final class AiSpeechResult {

    @NonNull
    private final String text;
    @NonNull
    private final AiProvider provider;
    private final long costMs;

    public AiSpeechResult(@NonNull String text, @NonNull AiProvider provider, long costMs) {
        this.text = text.trim();
        this.provider = provider;
        this.costMs = costMs;
    }

    @NonNull
    public String getText() {
        return text;
    }

    /** 是否识别出文字（为假时提示“没有听清，请再说一遍”） */
    public boolean isRecognized() {
        return !text.isEmpty();
    }

    @NonNull
    public AiProvider getProvider() {
        return provider;
    }

    public long getCostMs() {
        return costMs;
    }
}
