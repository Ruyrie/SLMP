package com.zhimu.slmp.common.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 模型回答：只取 choices[0].message.content（推理模型的 reasoning_content 忽略）、实际使用的服务商与耗时。
 */
public final class AiChatResult {

    @NonNull
    private final String content;
    @NonNull
    private final AiProvider provider;
    @Nullable
    private final String finishReason;
    private final long costMs;
    /** 主用失败、由备用服务商给出的回答 */
    private final boolean fallback;

    public AiChatResult(@NonNull String content, @NonNull AiProvider provider, @Nullable String finishReason,
                        long costMs, boolean fallback) {
        this.content = content;
        this.provider = provider;
        this.finishReason = finishReason;
        this.costMs = costMs;
        this.fallback = fallback;
    }

    @NonNull
    public String getContent() {
        return content;
    }

    @NonNull
    public AiProvider getProvider() {
        return provider;
    }

    @Nullable
    public String getFinishReason() {
        return finishReason;
    }

    public long getCostMs() {
        return costMs;
    }

    public boolean isFallback() {
        return fallback;
    }

    /** “测试直连 · 通义千问 qwen3.8-max” */
    @NonNull
    public String sourceLabel() {
        return "测试直连 · " + provider.getDisplayName() + " " + provider.getModel();
    }
}
