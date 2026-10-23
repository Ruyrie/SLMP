package com.zhimu.server.common.enums;

import lombok.Getter;

/** 文件种类 */
@Getter
public enum FileKind implements CodeEnum {
    IMAGE("图片"),
    VIDEO("视频"),
    AUDIO("音频"),
    DOCUMENT("导出文档");

    private final String text;

    FileKind(String text) {
        this.text = text;
    }
}