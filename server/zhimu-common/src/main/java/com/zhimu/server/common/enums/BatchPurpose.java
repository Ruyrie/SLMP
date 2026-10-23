package com.zhimu.server.common.enums;

import lombok.Getter;

/** 批次用途 */
@Getter
public enum BatchPurpose implements CodeEnum {
    COMMERCIAL("商品"),
    BREEDING("种用");

    private final String text;

    BatchPurpose(String text) {
        this.text = text;
    }
}