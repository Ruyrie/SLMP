package com.zhimu.server.biz.account.request;

/** API-A09 短信重置密码 */
public record PasswordResetRequest(String phone, String smsCode, String newPassword) {
}
