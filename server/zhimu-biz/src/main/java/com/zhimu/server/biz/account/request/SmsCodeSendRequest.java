package com.zhimu.server.biz.account.request;

/**
 * API-A01 发送验证码（未登录）：RECOVERY_NEW_PHONE 场景需要 old_phone。
 */
public record SmsCodeSendRequest(String phone, String scene, String oldPhone) {
}
