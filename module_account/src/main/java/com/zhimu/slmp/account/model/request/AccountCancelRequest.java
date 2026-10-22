package com.zhimu.slmp.account.model.request;

import androidx.annotation.NonNull;

/**
 * API-A20 注销账号：checklist_version 取 A19 的值。
 */
public class AccountCancelRequest {

    private final String smsCode;
    private final String checklistVersion;
    private final boolean confirmed;

    public AccountCancelRequest(@NonNull String smsCode, @NonNull String checklistVersion, boolean confirmed) {
        this.smsCode = smsCode;
        this.checklistVersion = checklistVersion;
        this.confirmed = confirmed;
    }

    /** 换成另一个验证码的副本（测试直连校验通过后提交给 Mock 用），相同时返回自身 */
    @NonNull
    public AccountCancelRequest withSmsCode(@NonNull String code) {
        return code.equals(smsCode) ? this : new AccountCancelRequest(code, checklistVersion, confirmed);
    }

    public String getSmsCode() {
        return smsCode;
    }

    public String getChecklistVersion() {
        return checklistVersion;
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}
