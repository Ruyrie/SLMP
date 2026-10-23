package com.zhimu.server.integration.ai;

/**
 * token 用量（服务商未返回的项为 null）。
 */
public record AiUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
}
