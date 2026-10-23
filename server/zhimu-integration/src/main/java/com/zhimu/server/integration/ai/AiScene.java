package com.zhimu.server.integration.ai;

/**
 * AI 调用场景：用于日志与默认超时（04 号 9.2 节）。
 */
public enum AiScene {
    /** 异常诊断（识图，60 秒） */
    DIAGNOSIS,
    /** AI 问答（流式，120 秒） */
    CHAT,
    /** 出栏建议（60 秒） */
    SALE_ADVICE,
    /** 报表摘要（60 秒） */
    REPORT_SUMMARY,
    /** 语音转文字（15 秒） */
    ASR
}
