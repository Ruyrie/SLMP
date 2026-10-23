package com.zhimu.server.biz.account.request;

/** API-A17 修改密码 */
public record PasswordChangeRequest(String currentPassword, String newPassword, String confirmPassword) {
}
