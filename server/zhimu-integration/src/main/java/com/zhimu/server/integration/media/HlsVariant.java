package com.zhimu.server.integration.media;

import lombok.Getter;

/**
 * 课程 HLS 档位（04 号 8.3 节）：短边 720 / 480（不放大），H.264 + AAC，每段 6 秒。
 */
@Getter
public enum HlsVariant {
    P720("720p", 720, 1500, 128),
    P480("480p", 480, 800, 96);

    /** 目录名与 t_file_variant 的档位编码 */
    private final String code;
    /** 短边像素 */
    private final int shortSide;
    private final int videoKbps;
    private final int audioKbps;

    HlsVariant(String code, int shortSide, int videoKbps, int audioKbps) {
        this.code = code;
        this.shortSide = shortSide;
        this.videoKbps = videoKbps;
        this.audioKbps = audioKbps;
    }
}
