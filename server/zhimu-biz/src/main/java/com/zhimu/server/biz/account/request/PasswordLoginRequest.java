package com.zhimu.server.biz.account.request;

/** API-A04 密码登录（正常密码或临时密码） */
public record PasswordLoginRequest(String phone, String password, String deviceId, String deviceModel,
                                   String osVersion) {
}
