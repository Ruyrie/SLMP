package com.zhimu.server.biz.account.enums;

import lombok.Getter;

/** 登录方式（t_login_log.login_type） */
@Getter
public enum LoginType {
    PASSWORD("密码"),
    SMS("验证码"),
    TEMP_PASSWORD("临时密码"),
    REGISTER("注册");

    private final String text;

    LoginType(String text) {
        this.text = text;
    }
}
