package com.zhimu.server.common.enums;

import lombok.Getter;

/** 修改申请状态 */
@Getter
public enum ChangeStatus implements CodeEnum {
    PENDING("待审核"),
    APPROVED("已通过"),
    REJECTED("已驳回"),
    CANCELED("已撤销");

    private final String text;

    ChangeStatus(String text) {
        this.text = text;
    }
}