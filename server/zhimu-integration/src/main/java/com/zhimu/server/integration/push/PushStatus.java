package com.zhimu.server.integration.push;

/**
 * 推送调用结果。
 */
public enum PushStatus {
    /** 极光已受理（推送返回 msg_id；设备别名设置成功） */
    SENT,
    /** 未发送：未配置 Master Secret，或没有满足条件的设备（极光 1011） */
    SKIPPED,
    /** 频率超限（极光 2002 / HTTP 429），由调用方稍后补发 */
    RATE_LIMITED,
    /** 失败（鉴权错误、参数错误，或超时与 5xx 重试 2 次后仍失败） */
    FAILED
}
