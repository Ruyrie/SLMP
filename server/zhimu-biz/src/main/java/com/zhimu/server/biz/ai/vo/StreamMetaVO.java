package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * SSE meta：轮次落库后第一条。
 */
@Data
public class StreamMetaVO {

    private Long messageId;
    private Long sessionId;
    private int roundNo;
    private boolean sessionCreated;
    /** NONE / VALID / NOT_IN_STOCK / NO_PERMISSION */
    private String batchLinkState;
}
