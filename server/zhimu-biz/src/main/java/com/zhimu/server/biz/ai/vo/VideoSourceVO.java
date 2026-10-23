package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * 一档清晰度的播放地址。
 */
@Data
public class VideoSourceVO {

    /** P720 / P480 */
    private String quality;
    private String hlsUrl;
    private long sizeBytes;
}
