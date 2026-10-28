package com.zhimu.server.integration.ai;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 网关配置（zhimu.integration.ai）。Key 为空的服务商不参与调用；两个都为空时网关关闭。
 */
@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = "zhimu.integration.ai")
public class AiProperties {

    private boolean enabled = true;
    /** 全局并发上限 */
    private int maxConcurrency = 20;
    /** 主模型（阿里云百炼），语音识别也只走这里 */
    private Provider primary = new Provider();
    /** 备用模型（DeepSeek） */
    private Provider fallback = new Provider();
    private Duration diagnosisTimeout = Duration.ofSeconds(120);
    private Duration chatTimeout = Duration.ofSeconds(120);
    private Duration asrTimeout = Duration.ofSeconds(15);
    private Duration adviceTimeout = Duration.ofSeconds(60);

    /** 场景对应的默认超时；报表摘要与出栏建议同为 advice-timeout */
    public Duration timeoutOf(AiScene scene) {
        return switch (scene) {
            case DIAGNOSIS -> diagnosisTimeout;
            case CHAT -> chatTimeout;
            case ASR -> asrTimeout;
            case SALE_ADVICE, REPORT_SUMMARY -> adviceTimeout;
        };
    }

    @Getter
    @Setter
    @ToString
    public static class Provider {
        private String name;
        private String baseUrl;
        @ToString.Exclude
        private String apiKey;
        private String chatModel;
        private String visionModel;
        /** 只有主模型使用 */
        private String asrModel;

        /** Key 与地址都已配置 */
        public boolean available() {
            return hasText(apiKey) && hasText(baseUrl);
        }

        /** 带图片用识图模型（未配置时退回对话模型） */
        public String modelFor(boolean vision) {
            return vision && hasText(visionModel) ? visionModel : chatModel;
        }

        public String chatCompletionsUrl() {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return base + "/chat/completions";
        }

        private static boolean hasText(String s) {
            return s != null && !s.isBlank();
        }
    }
}
