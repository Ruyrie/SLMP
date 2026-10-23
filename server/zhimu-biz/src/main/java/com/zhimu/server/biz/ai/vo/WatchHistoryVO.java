package com.zhimu.server.biz.ai.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * API-K21 观看记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WatchHistoryVO extends CourseCardVO {

    private String lastWatchedAt;
}
