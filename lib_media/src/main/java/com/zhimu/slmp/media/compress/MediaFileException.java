package com.zhimu.slmp.media.compress;

import androidx.annotation.NonNull;

/**
 * 文件不符合要求（类型、大小或无法读取）；message 为可直接显示给用户的文案（02 号规范 10.1 节）。
 */
public class MediaFileException extends Exception {

    /** 02 号规范 10.1 节：从相册选择的图片不符合要求 */
    public static final String MSG_IMAGE_INVALID = "只支持 JPG 或 PNG 图片，单张不超过 5MB";
    public static final String MSG_UNREADABLE = "图片无法读取，请重新选择";

    public MediaFileException(@NonNull String message) {
        super(message);
    }

    public MediaFileException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
    }
}
