package com.zhimu.slmp.ai.model;

import androidx.annotation.NonNull;

/**
 * ViewModel 下发给页面的播放指令：换媒体项（HLS 地址与清晰度）并从 startMs 开始；playWhenReady 为假时只准备不播放（仅 Wi-Fi 拦截）。
 */
public final class PlayCommand {

    @NonNull
    private final String hlsUrl;
    @NonNull
    private final String quality;
    private final long startMs;
    private final boolean playWhenReady;

    public PlayCommand(@NonNull String hlsUrl, @NonNull String quality, long startMs, boolean playWhenReady) {
        this.hlsUrl = hlsUrl;
        this.quality = quality;
        this.startMs = startMs;
        this.playWhenReady = playWhenReady;
    }

    @NonNull
    public String getHlsUrl() {
        return hlsUrl;
    }

    @NonNull
    public String getQuality() {
        return quality;
    }

    public long getStartMs() {
        return startMs;
    }

    public boolean isPlayWhenReady() {
        return playWhenReady;
    }
}
