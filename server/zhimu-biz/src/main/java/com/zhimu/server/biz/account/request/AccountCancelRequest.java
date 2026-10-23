package com.zhimu.server.biz.account.request;

/** API-A20 注销账号；checklist_version 取 API-A19 的值。 */
public record AccountCancelRequest(String smsCode, Boolean confirmed, String checklistVersion) {
}
