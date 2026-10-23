package com.zhimu.server.biz.account.request;

/** API-A13 短信验证后重新生成恢复码 */
public record RecoveryCodeRegenerateRequest(String smsCode) {
}
