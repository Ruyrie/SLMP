package com.zhimu.server.biz.account.enums;

import lombok.Getter;

/** 登录失败原因（t_login_log.fail_reason） */
@Getter
public enum LoginFailReason {
    PHONE_NOT_REGISTERED("手机号未注册"),
    PASSWORD_WRONG("密码错误"),
    SMS_CODE_WRONG("验证码错误"),
    SMS_CODE_EXPIRED("验证码过期"),
    LOGIN_LOCKED("密码锁定"),
    ACCOUNT_DISABLED("账号停用"),
    TEMP_PASSWORD_EXPIRED("临时密码过期");

    private final String text;

    LoginFailReason(String text) {
        this.text = text;
    }
}
