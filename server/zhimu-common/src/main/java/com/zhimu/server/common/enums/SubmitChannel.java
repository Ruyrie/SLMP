package com.zhimu.server.common.enums;

import lombok.Getter;

/** 提交渠道 */
@Getter
public enum SubmitChannel implements CodeEnum {
    APP_ANDROID("App"),
    WEB("Web 用户端"),
    CS_PROXY("客服代登记");

    private final String text;

    SubmitChannel(String text) {
        this.text = text;
    }
}