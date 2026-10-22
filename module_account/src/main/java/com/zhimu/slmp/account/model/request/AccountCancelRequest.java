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
