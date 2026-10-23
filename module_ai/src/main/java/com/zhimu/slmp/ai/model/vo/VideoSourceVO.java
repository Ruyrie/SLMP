package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一档清晰度的 HLS 播放列表。quality：P720 / P480
 */
public class VideoSourceVO {

    private String quality;
    private String hlsUrl;
    private long sizeBytes;

    @Nullable
    public String getQuality() {
        return quality;
    }

    @Nullable
    public String getHlsUrl() {
        return hlsUrl;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setQuality(@Nullable String quality) {
        this.quality = quality;
    }

    public void setHlsUrl(@Nullable String hlsUrl) {
        this.hlsUrl = hlsUrl;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}
