package com.zhimu.slmp.common.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一个 SSE 事件：event 行的事件名（没有 event 行时为 null）与 data 行原文（多行 data 以换行连接）；
 * 冒号开头的注释行（心跳）作为 {@link #isComment()} 为真的事件单独派发，便于调用方重置看门狗。
 */
public final class AiSseEvent {

    @Nullable
    private final String event;
    @NonNull
    private final String data;
    private final boolean comment;

    AiSseEvent(@Nullable String event, @NonNull String data, boolean comment) {
        this.event = event;
        this.data = data;
        this.comment = comment;
    }

    @NonNull
    public static AiSseEvent of(@Nullable String event, @NonNull String data) {
        return new AiSseEvent(event, data, false);
    }

    /** 事件名；OpenAI 兼容接口没有 event 行，为 null */
    @Nullable
    public String getEvent() {
        return event;
    }

    /** data 原文（注释行为注释内容） */
    @NonNull
    public String getData() {
        return data;
    }

    /** 心跳注释行 */
    public boolean isComment() {
        return comment;
    }

    /** OpenAI 兼容接口的结束标记 data: [DONE] */
    public boolean isDoneMarker() {
        return !comment && "[DONE]".equals(data.trim());
    }

    @NonNull
    @Override
    public String toString() {
        return comment ? "SSE(:comment)" : "SSE(" + event + ", " + data.length() + " chars)";
    }
}
