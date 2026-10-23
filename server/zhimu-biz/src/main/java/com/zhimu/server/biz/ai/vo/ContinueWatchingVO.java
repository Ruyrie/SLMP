package com.zhimu.server.biz.ai.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * API-K15 继续观看（没有在看的视频时 data 为 null）。
 */
@Data
public class ContinueWatchingVO {

    private Long courseId;
    private String title;
    private String coverUrl;
    private String categoryName;
    private int durationSec;
    private int positionSec;
    private int remainingSec;
    private BigDecimal progressRatio;
    private String topicName;
    private Integer sectionNo;
}
