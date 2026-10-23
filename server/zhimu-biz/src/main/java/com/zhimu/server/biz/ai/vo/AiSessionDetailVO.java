package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * API-K02 / K04 会话详情；batch 仅在 batch_link_state 为 VALID 时返回。
 */
@Data
public class AiSessionDetailVO {

    private Long id;
    private String title;
    private int roundCount;
    private boolean hasCorrection;
    private boolean contextTruncated;
    private boolean hasEpidemicAlert;
    /** NONE / VALID / NOT_IN_STOCK / NO_PERMISSION */
    private String batchLinkState;
    private int lastRoundNo;
    private LinkedBatchVO batch;
    private String invalidBatchNo;
}
