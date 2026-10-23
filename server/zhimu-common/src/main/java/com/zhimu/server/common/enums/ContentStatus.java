package com.zhimu.server.common.enums;

import lombok.Getter;

/** 内容状态 */
@Getter
public enum ContentStatus implements CodeEnum {
    DRAFT("草稿"),
    ON_SHELF("已上架"),
    OFF_SHELF("已下架");

    private final String text;

    ContentStatus(String text) {
        this.text = text;
    }
}