package com.zhimu.server.biz.account.request;

/** API-A03 注册 */
public record RegisterRequest(String phone, String smsCode, String password, Boolean agreeTerms,
                              String agreementVersion, String deviceId, String deviceModel, String osVersion) {
}
