package com.zhimu.server.integration.media;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FFmpeg 配置（zhimu.integration.ffmpeg）。
 */
@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = "zhimu.integration.ffmpeg")
public class FfmpegProperties {

    private String ffmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";
    /** 课程视频转码线程数 */
    private int transcodeThreads = 2;
}
