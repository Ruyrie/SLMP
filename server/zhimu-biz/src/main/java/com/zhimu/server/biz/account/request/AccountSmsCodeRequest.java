package com.zhimu.server.biz.account.request;

/** API-A02 发送验证码到本人手机号：scene 为 REGENERATE_RECOVERY_CODE 或 CANCEL_ACCOUNT。 */
public record AccountSmsCodeRequest(String scene) {
}
