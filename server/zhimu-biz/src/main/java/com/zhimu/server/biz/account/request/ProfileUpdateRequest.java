package com.zhimu.server.biz.account.request;

/** API-A16 修改姓名；version 取 API-A15 的 profile_version。 */
public record ProfileUpdateRequest(String name, Integer version) {
}
