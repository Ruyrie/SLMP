package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * API-K10 语音转文字结果。
 */
@Data
public class SpeechResultVO {

    private boolean recognized;
    private String text;
    private int durationMs;
}
