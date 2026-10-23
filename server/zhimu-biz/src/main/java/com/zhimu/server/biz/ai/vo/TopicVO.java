package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * API-K16 专题卡。
 */
@Data
public class TopicVO {

    private Long id;
    private String name;
    private String categoryName;
    private String coverUrl;
    private int courseCount;
    private int totalDurationSec;
    private int learnedCount;
    private String lastLearnedAt;
}
