package com.zhimu.server.integration.ai;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 一次调用的结果。
 */
@Getter
@Builder
@ToString(exclude = "content")
public class AiChatResult {

    /** 回答正文（只取 content，不含 reasoning_content） */
    private final String content;
    /** 服务商名称（配置中的 name，如 dashscope、deepseek） */
    private final String provider;
    /** 响应中的 model 字段；服务商未返回时为请求的模型名 */
    private final String model;
    /** 是否用了备用服务商 */
    private final boolean fallback;
    private final String finishReason;
    private final AiUsage usage;
    /** 从发起到结束的耗时（含切换备用前的失败调用） */
    private final long costMs;
}
