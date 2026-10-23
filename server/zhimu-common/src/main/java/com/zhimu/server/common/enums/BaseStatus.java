package com.zhimu.server.common.enums;

import lombok.Getter;

/** 启用状态 */
@Getter
public enum BaseStatus implements CodeEnum {
    ENABLED("启用"),
    DISABLED("已停用");

    private final String text;

    BaseStatus(String text) {
        this.text = text;
    }
}