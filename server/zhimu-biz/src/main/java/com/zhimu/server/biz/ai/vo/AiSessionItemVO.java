package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * API-K01 历史会话列表项。
 */
@Data
public class AiSessionItemVO {

    private Long id;
    private String title;
    /** AI_TOPIC */
    private String topic;
    private String topicText;
    private String batchNo;
    private int roundCount;
    private String lastAskedAt;
    /** TODAY / YESTERDAY / LAST_7_DAYS / EARLIER */
    private String dateGroup;
    private String dateGroupText;
    private boolean hasCorrection;
    private boolean contextTruncated;
    private boolean hasEpidemicAlert;
}
