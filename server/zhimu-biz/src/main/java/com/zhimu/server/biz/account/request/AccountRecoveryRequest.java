package com.zhimu.server.biz.account.request;

/** API-A10 凭恢复码提交找回申请 */
public record AccountRecoveryRequest(String oldPhone, String recoveryCode, String newPhone,
                                     String newPhoneSmsCode, String description) {
}
