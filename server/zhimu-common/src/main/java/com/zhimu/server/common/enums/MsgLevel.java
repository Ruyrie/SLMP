package com.zhimu.server.common.enums;

import lombok.Getter;

/** 消息级别 */
@Getter
public enum MsgLevel implements CodeEnum {
    NORMAL("普通"),
    IMPORTANT("重要"),
    URGENT("紧急");

    private final String text;

    MsgLevel(String text) {
        this.text = text;
    }
}