package com.zhimu.slmp.common.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * {@link AiChatClient#stream} 推送的一段流式结果：
 * <ul>
 *     <li>{@link Type#STARTED}：服务商已接受请求（HTTP 200、开始读流）；换备用服务商时再发一次，{@link #isFallback()} 为真；</li>
 *     <li>{@link Type#THINKING}：推理模型正在思考（收到 reasoning_content，内容不对外提供，界面继续显示“正在思考”），也可用作心跳；</li>
 *     <li>{@link Type#CONTENT}：回答正文的增量 {@link #getText()}；</li>
 *     <li>{@link Type#DONE}：最后一条，{@link #getText()} 为完整正文，带 finish_reason 与耗时。</li>
 * </ul>
 */
public final class AiStreamChunk {

    public enum Type {
        STARTED,
        THINKING,
        CONTENT,
        DONE
    }

    @NonNull
    private final Type type;
    @NonNull
    private final String text;
    @NonNull
    private final AiProvider provider;
    private final boolean fallback;
    @Nullable
    private final String finishReason;
    private final long costMs;

    private AiStreamChunk(@NonNull Type type, @NonNull String text, @NonNull AiProvider provider, boolean fallback,
                          @Nullable String finishReason, long costMs) {
        this.type = type;
        this.text = text;
        this.provider = provider;
        this.fallback = fallback;
        this.finishReason = finishReason;
        this.costMs = costMs;
    }

    @NonNull
    public static AiStreamChunk started(@NonNull AiProvider provider, boolean fallback) {
        return new AiStreamChunk(Type.STARTED, "", provider, fallback, null, 0);
    }

    @NonNull
    public static AiStreamChunk thinking(@NonNull AiProvider provider, boolean fallback) {
        return new AiStreamChunk(Type.THINKING, "", provider, fallback, null, 0);
    }

    @NonNull
    public static AiStreamChunk content(@NonNull String delta, @NonNull AiProvider provider, boolean fallback) {
        return new AiStreamChunk(Type.CONTENT, delta, provider, fallback, null, 0);
    }

    @NonNull
    public static AiStreamChunk done(@NonNull String fullText, @NonNull AiProvider provider, boolean fallback,
                                     @Nullable String finishReason, long costMs) {
        return new AiStreamChunk(Type.DONE, fullText, provider, fallback, finishReason, costMs);
    }

    @NonNull
    public Type getType() {
        return type;
    }

    /** CONTENT 为增量，DONE 为完整正文，其余为空串 */
    @NonNull
    public String getText() {
        return text;
    }

    @NonNull
    public AiProvider getProvider() {
        return provider;
    }

    /** 主用失败、由备用服务商给出 */
    public boolean isFallback() {
        return fallback;
    }

    @Nullable
    public String getFinishReason() {
        return finishReason;
    }

    /** DONE：从发出请求到结束的耗时 */
    public long getCostMs() {
        return costMs;
    }

    @NonNull
    @Override
    public String toString() {
        return "AiStreamChunk(" + type + ", " + text.length() + " chars, " + provider + ")";
    }
}
