package com.zhimu.server.common.enums;

import lombok.Getter;

/** 审核状态 */
@Getter
public enum AuditStatus implements CodeEnum {
    PENDING("待审核"),
    APPROVED("已通过"),
    REJECTED("已驳回"),
    CANCELED("已撤销");

    private final String text;

    AuditStatus(String text) {
        this.text = text;
    }
}