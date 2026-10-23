package com.zhimu.slmp.common.ai;

import androidx.annotation.NonNull;

/**
 * 多轮对话中的一条历史消息（只有文字）：放在系统提示词之后、本次提问之前，按时间顺序排列。
 * 模块11 AI 问答把最近 10 轮问答作为上下文时使用；图片不随历史重复发送。
 */
public final class AiTurn {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    @NonNull
    private final String role;
    @NonNull
    private final String content;

    private AiTurn(@NonNull String role, @NonNull String content) {
        this.role = role;
        this.content = content;
    }

    @NonNull
    public static AiTurn user(@NonNull String content) {
        return new AiTurn(ROLE_USER, content);
    }

    @NonNull
    public static AiTurn assistant(@NonNull String content) {
        return new AiTurn(ROLE_ASSISTANT, content);
    }

    @NonNull
    public String getRole() {
        return role;
    }

    @NonNull
    public String getContent() {
        return content;
    }
}
