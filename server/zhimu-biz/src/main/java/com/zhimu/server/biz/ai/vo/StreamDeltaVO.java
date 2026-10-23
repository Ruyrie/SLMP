package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * SSE delta：回答片段。
 */
@Data
public class StreamDeltaVO {

    private String text;

    public StreamDeltaVO() {
    }

    public StreamDeltaVO(String text) {
        this.text = text;
    }
}
