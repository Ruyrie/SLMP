package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 文件上传结果与业务记录中的照片、视频（05 号 7.2 节）；url 为临时地址，加载图片时以 file_id 作缓存键。
 */
public class FileVO {

    private long fileId;
    @Nullable
    private String url;
    @Nullable
    private String thumbUrl;
    /** IMAGE / VIDEO / AUDIO / DOCUMENT */
    @Nullable
    private String mediaType;
    @Nullable
    private String mimeType;
    /** 字节 */
    private long size;
    @Nullable
    private Integer width;
    @Nullable
    private Integer height;
    /** 视频、音频时长（秒） */
    @Nullable
    private Integer durationSec;
    /** UPLOADING / READY / FAILED */
    @Nullable
    private String status;

    public long getFileId() {
        return fileId;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    @Nullable
    public String getThumbUrl() {
        return thumbUrl;
    }

    @Nullable
    public String getMediaType() {
        return mediaType;
    }

    @Nullable
    public String getMimeType() {
        return mimeType;
    }

    public long getSize() {
        return size;
    }

    @Nullable
    public Integer getWidth() {
        return width;
    }

    @Nullable
    public Integer getHeight() {
        return height;
    }

    @Nullable
    public Integer getDurationSec() {
        return durationSec;
    }

    @Nullable
    public String getStatus() {
        return status;
    }
}
