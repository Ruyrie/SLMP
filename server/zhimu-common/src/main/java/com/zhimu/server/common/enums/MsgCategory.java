package com.zhimu.server.common.enums;

import lombok.Getter;

/** 消息分类（消息中心 7 类筛选） */
@Getter
public enum MsgCategory implements CodeEnum {
    TODO("待办提醒"),
    AI("AI 诊断"),
    AUDIT("审核结果"),
    MEMBER("成员与养殖场"),
    LOT("批号停用"),
    NOTICE("平台通知"),
    OTHER("其他");

    private final String text;

    MsgCategory(String text) {
        this.text = text;
    }
}