package com.zhimu.server.common.enums;

import lombok.Getter;

/** 文件派生版本 */
@Getter
public enum VariantCode implements CodeEnum {
    HLS_720P("720P"),
    HLS_480P("480P"),
    MP4_720P("720p 压缩版"),
    THUMB("缩略图");

    private final String text;

    VariantCode(String text) {
        this.text = text;
    }
}