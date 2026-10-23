package com.zhimu.server.integration.ai;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.time.Duration;
import java.util.List;

/**
 * 一次 Chat Completions 请求。
 * <pre>
 * AiChatRequest.builder()
 *         .scene(AiScene.DIAGNOSIS)
 *         .message(AiMessage.system(prompt))
 *         .message(AiMessage.user(text, List.of(dataUrl)))
 *         .jsonMode(true)
 *         .maxTokens(2000)
 *         .build();
 * </pre>
 */
@Getter
@Builder
public class AiChatRequest {

    @Builder.Default
    private final AiScene scene = AiScene.CHAT;
    @Singular
    private final List<AiMessage> messages;
    /** 开启时 response_format 为 json_object（提示词中需要出现“JSON”） */
    private final boolean jsonMode;
    /** 不设时不发送（部分推理模型不支持） */
    private final Double temperature;
    /** 推理模型的思考也计入输出，建议不少于 1500；不设时用服务商默认值 */
    private final Integer maxTokens;
    /** 不设时按场景取配置中的超时 */
    private final Duration timeout;

    /** 任一消息带图片 */
    public boolean hasImages() {
        return messages.stream().anyMatch(AiMessage::hasImages);
    }

    public int imageCount() {
        return messages.stream().mapToInt(m -> m.getImages().size()).sum();
    }
}
