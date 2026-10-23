package com.zhimu.server.biz.account.request;

/** API-A05 验证码登录 */
public record SmsLoginRequest(String phone, String smsCode, String deviceId, String deviceModel, String osVersion) {
}
