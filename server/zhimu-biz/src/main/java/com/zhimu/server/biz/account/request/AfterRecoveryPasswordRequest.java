package com.zhimu.server.biz.account.request;

/** API-A11 找回后修改密码 */
public record AfterRecoveryPasswordRequest(String newPassword, String confirmPassword) {
}
