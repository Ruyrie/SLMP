package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * 知识原文内嵌的教学视频。
 */
@Data
public class EmbeddedVideoVO {

    private Long courseId;
    private String title;
    private int durationSec;
    private String coverUrl;
}
