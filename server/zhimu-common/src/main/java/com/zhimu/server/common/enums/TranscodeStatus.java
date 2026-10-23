package com.zhimu.server.common.enums;

import lombok.Getter;

/** 转码状态 */
@Getter
public enum TranscodeStatus implements CodeEnum {
    NONE("无需转码"),
    WAITING("等待中"),
    PROCESSING("转码中"),
    SUCCESS("已完成"),
    FAILED("转码失败");

    private final String text;

    TranscodeStatus(String text) {
        this.text = text;
    }
}