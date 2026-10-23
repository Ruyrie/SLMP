package com.zhimu.server.biz.account.enums;

import lombok.Getter;

/** 账号状态（t_user.status） */
@Getter
public enum UserStatus {
    NORMAL("正常"),
    DISABLED("已停用"),
    CANCELLED("已注销");

    private final String text;

    UserStatus(String text) {
        this.text = text;
    }
}
