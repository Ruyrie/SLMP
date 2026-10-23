package com.zhimu.server.biz.account.enums;

import lombok.Getter;

/** 短信验证码场景（按场景隔离 Redis 键） */
@Getter
public enum SmsScene {
    LOGIN("登录"),
    REGISTER("注册"),
    RESET_PASSWORD("重置密码"),
    RECOVERY_NEW_PHONE("找回账号"),
    REGENERATE_RECOVERY_CODE("重新生成恢复码"),
    CANCEL_ACCOUNT("注销账号");

    private final String text;

    SmsScene(String text) {
        this.text = text;
    }
}
