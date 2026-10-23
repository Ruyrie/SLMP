package com.zhimu.server.common.enums;

import lombok.Getter;

/** 养殖场状态（派生，只出现在 VO 与上下文） */
@Getter
public enum FarmStatus implements CodeEnum {
    PENDING("待审核"),
    REJECTED("已驳回"),
    NORMAL("正常"),
    DISABLED("已停用"),
    FROZEN("已冻结");

    private final String text;

    FarmStatus(String text) {
        this.text = text;
    }
}