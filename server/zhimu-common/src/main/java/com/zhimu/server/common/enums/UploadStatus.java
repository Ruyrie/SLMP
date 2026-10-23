package com.zhimu.server.common.enums;

import lombok.Getter;

/** 上传状态 */
@Getter
public enum UploadStatus implements CodeEnum {
    UPLOADING("上传中"),
    READY("已上传"),
    FAILED("上传失败");

    private final String text;

    UploadStatus(String text) {
        this.text = text;
    }
}