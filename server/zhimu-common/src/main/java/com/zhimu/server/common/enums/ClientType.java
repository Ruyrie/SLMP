package com.zhimu.server.common.enums;

import lombok.Getter;

/** 客户端类型（请求头 X-Client） */
@Getter
public enum ClientType implements CodeEnum {
    APP_ANDROID("App"),
    WEB("Web 用户端"),
    ADMIN_WEB("Web 管理端");

    private final String text;

    ClientType(String text) {
        this.text = text;
    }
}