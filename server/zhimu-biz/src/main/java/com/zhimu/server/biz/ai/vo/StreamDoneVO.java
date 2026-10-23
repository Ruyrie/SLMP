package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * SSE done：完整轮次。
 */
@Data
public class StreamDoneVO {

    private String answerType;
    private String finishReason;
    private AiMessageVO message;
}
