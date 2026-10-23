package com.zhimu.server.integration.sms;

/**
 * 发送成功的结果（不含验证码本身）。
 *
 * @param bizId           阿里云发送回执 ID（排查用；mock 为 null）
 * @param requestId       阿里云 RequestId（排查用；mock 为 null）
 * @param intervalSeconds 再次发送的间隔秒数
 * @param validSeconds    验证码有效期秒数
 */
public record SmsSendResult(String bizId, String requestId, int intervalSeconds, int validSeconds) {
}
