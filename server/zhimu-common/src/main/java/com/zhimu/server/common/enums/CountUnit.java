package com.zhimu.server.common.enums;

import lombok.Getter;

/** 计数单位 */
@Getter
public enum CountUnit implements CodeEnum {
    HEAD("头"),
    BIRD("只");

    private final String text;

    CountUnit(String text) {
        this.text = text;
    }
}