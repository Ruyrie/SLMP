package com.zhimu.server.biz.account.enums;

import lombok.Getter;

/** 找回结果短信发送状态 */
@Getter
public enum SmsSendStatus {
    PENDING("待发送"),
    SENT("已发送"),
    FAILED("发送失败");

    private final String text;

    SmsSendStatus(String text) {
        this.text = text;
    }
}
