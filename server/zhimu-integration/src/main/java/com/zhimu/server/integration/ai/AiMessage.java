package com.zhimu.server.integration.ai;

import lombok.Getter;

import java.util.List;

/**
 * 一条对话消息。图片可以是 data URL（data:image/jpeg;base64,...），也可以是模型服务能访问的 http(s) 地址。
 */
@Getter
public final class AiMessage {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private final String role;
    private final String text;
    private final List<String> images;

    private AiMessage(String role, String text, List<String> images) {
        this.role = role;
        this.text = text == null ? "" : text;
        this.images = images == null ? List.of() : List.copyOf(images);
    }

    public static AiMessage system(String text) {
        return new AiMessage(ROLE_SYSTEM, text, null);
    }

    public static AiMessage user(String text) {
        return new AiMessage(ROLE_USER, text, null);
    }

    public static AiMessage user(String text, List<String> images) {
        return new AiMessage(ROLE_USER, text, images);
    }

    public static AiMessage assistant(String text) {
        return new AiMessage(ROLE_ASSISTANT, text, null);
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }
}
