package com.zhimu.server.common.enums;

import lombok.Getter;

/** 操作人类型 */
@Getter
public enum OperatorType implements CodeEnum {
    USER("用户"),
    ADMIN("管理员"),
    SYSTEM("系统");

    private final String text;

    OperatorType(String text) {
        this.text = text;
    }
}