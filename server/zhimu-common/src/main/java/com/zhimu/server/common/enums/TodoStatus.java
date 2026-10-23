package com.zhimu.server.common.enums;

import lombok.Getter;

/** 待办状态（OVERDUE 由后端按截止时刻计算后返回） */
@Getter
public enum TodoStatus implements CodeEnum {
    PENDING("待处理"),
    DONE("已完成"),
    IGNORED("已忽略"),
    OVERDUE("已逾期");

    private final String text;

    TodoStatus(String text) {
        this.text = text;
    }
}