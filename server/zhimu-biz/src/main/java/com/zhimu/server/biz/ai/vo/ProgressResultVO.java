package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * API-K20 进度上报结果。
 */
@Data
public class ProgressResultVO {

    private int positionSec;
    private int maxPositionSec;
    private java.math.BigDecimal progressRatio;
    private boolean completed;
    private boolean justCompleted;
    /** NOT_STARTED / LEARNING / COMPLETED */
    private String learnStatus;
    private Integer topicLearnedCount;
}
