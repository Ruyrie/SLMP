package com.zhimu.server.common.enums;

import lombok.Getter;

/** 修改申请类型 */
@Getter
public enum ChangeType implements CodeEnum {
    UPDATE("修改"),
    DELETE("删除"),
    VOID("作废");

    private final String text;

    ChangeType(String text) {
        this.text = text;
    }
}