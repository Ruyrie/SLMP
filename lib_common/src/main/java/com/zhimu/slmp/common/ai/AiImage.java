package com.zhimu.slmp.common.ai;

import android.graphics.Bitmap;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;

/**
 * 请求中的一张图片：压缩后的 JPEG，以 base64 data URL（data:image/jpeg;base64,...）放入 image_url。
 * 两家服务商都已实测能识别 base64 图片；视频请先截取若干关键帧再逐张加入。
 */
public final class AiImage {

    /** 默认长边（像素）：足够识别病征，请求体又不会太大 */
    public static final int DEFAULT_MAX_SIDE = 1024;
    public static final int DEFAULT_QUALITY = 80;

    @NonNull
    private final String dataUrl;
    private final int byteCount;
    @NonNull
    private final String label;

    private AiImage(@NonNull String dataUrl, int byteCount, @NonNull String label) {
        this.dataUrl = dataUrl;
        this.byteCount = byteCount;
        this.label = label;
    }

    /** 已压缩好的 JPEG 字节 */
    @NonNull
    public static AiImage jpeg(@NonNull byte[] jpegBytes, @NonNull String label) {
        String b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP);
        return new AiImage("data:image/jpeg;base64," + b64, jpegBytes.length, label);
    }

    /** 缩放到长边 maxSide 并压成 JPEG（不回收传入的 Bitmap） */
    @WorkerThread
    @NonNull
    public static AiImage fromBitmap(@NonNull Bitmap bitmap, int maxSide, int quality, @NonNull String label) {
        Bitmap scaled = scale(bitmap, maxSide);
        ByteArrayOutputStream out = new ByteArrayOutputStream(128 * 1024);
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out);
        if (scaled != bitmap) {
            scaled.recycle();
        }
        return jpeg(out.toByteArray(), label);
    }

    @NonNull
    private static Bitmap scale(@NonNull Bitmap src, int maxSide) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longSide = Math.max(w, h);
        if (longSide <= maxSide || longSide <= 0) {
            return src;
        }
        float s = maxSide / (float) longSide;
        return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s)), true);
    }

    @NonNull
    public String getDataUrl() {
        return dataUrl;
    }

    /** 压缩后的字节数（日志只记大小） */
    public int getByteCount() {
        return byteCount;
    }

    /** 说明，例如“照片 1”“视频关键帧 0:06”，会作为文字放在图片前 */
    @NonNull
    public String getLabel() {
        return label;
    }
}
