package com.zhimu.server.common.enums;

import lombok.Getter;

/** 待办类别 */
@Getter
public enum TodoCategory implements CodeEnum {
    WORK("作业类"),
    WARNING("预警类"),
    BUSINESS("经营类");

    private final String text;

    TodoCategory(String text) {
        this.text = text;
    }
}