package com.zhimu.server.common.enums;

import lombok.Getter;

/** 记录状态（可作废的记录） */
@Getter
public enum RecordStatus implements CodeEnum {
    NORMAL("有效"),
    VOID("已作废");

    private final String text;

    RecordStatus(String text) {
        this.text = text;
    }
}