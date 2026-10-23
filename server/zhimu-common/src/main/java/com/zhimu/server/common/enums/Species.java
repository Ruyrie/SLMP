package com.zhimu.server.common.enums;

import lombok.Getter;

/** 畜种 */
@Getter
public enum Species implements CodeEnum {
    PIG("猪"),
    POULTRY("禽"),
    CATTLE("牛"),
    SHEEP("羊");

    private final String text;

    Species(String text) {
        this.text = text;
    }
}