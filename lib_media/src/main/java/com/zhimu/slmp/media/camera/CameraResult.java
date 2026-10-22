package com.zhimu.slmp.media.camera;

import android.content.Intent;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 相机页 I08 的返回结果（{@link CameraContract} 解析）。
 */
public final class CameraResult {

    /** 关闭或返回，没有拍照 */
    public static final int TYPE_CANCELED = 0;
    /** 拍了照片：{@link #getPhotoPaths()} 为 cacheDir/capture 下的 JPEG 文件 */
    public static final int TYPE_PHOTOS = 1;
    /** 识别到二维码：{@link #getScanText()} 为原文（由调用方解析，例如 L07 取 qr_token） */
    public static final int TYPE_SCAN = 2;
    /** 扫码页点了“手动输入”入口，由调用方打开输入面板 */
    public static final int TYPE_MANUAL_INPUT = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_CANCELED, TYPE_PHOTOS, TYPE_SCAN, TYPE_MANUAL_INPUT})
    public @interface Type {
    }

    static final String EXTRA_TYPE = "media_result_type";
    static final String EXTRA_PHOTOS = "media_result_photos";
    static final String EXTRA_SCAN_TEXT = "media_result_scan_text";

    @Type
    private final int type;
    @NonNull
    private final List<String> photoPaths;
    @Nullable
    private final String scanText;

    private CameraResult(@Type int type, @Nullable List<String> photoPaths, @Nullable String scanText) {
        this.type = type;
        this.photoPaths = photoPaths == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(photoPaths));
        this.scanText = scanText;
    }

    @NonNull
    public static CameraResult canceled() {
        return new CameraResult(TYPE_CANCELED, null, null);
    }

    @NonNull
    public static CameraResult photos(@NonNull List<String> paths) {
        return paths.isEmpty() ? canceled() : new CameraResult(TYPE_PHOTOS, paths, null);
    }

    @NonNull
    public static CameraResult scan(@NonNull String text) {
        return new CameraResult(TYPE_SCAN, null, text);
    }

    @NonNull
    public static CameraResult manualInput() {
        return new CameraResult(TYPE_MANUAL_INPUT, null, null);
    }

    @NonNull
    public Intent toIntent() {
        Intent data = new Intent();
        data.putExtra(EXTRA_TYPE, type);
        data.putStringArrayListExtra(EXTRA_PHOTOS, new ArrayList<>(photoPaths));
        data.putExtra(EXTRA_SCAN_TEXT, scanText);
        return data;
    }

    @NonNull
    static CameraResult fromIntent(@Nullable Intent data) {
        if (data == null) {
            return canceled();
        }
        int type = data.getIntExtra(EXTRA_TYPE, TYPE_CANCELED);
        switch (type) {
            case TYPE_PHOTOS:
                ArrayList<String> paths = data.getStringArrayListExtra(EXTRA_PHOTOS);
                return photos(paths == null ? Collections.emptyList() : paths);
            case TYPE_SCAN:
                String text = data.getStringExtra(EXTRA_SCAN_TEXT);
                return text == null ? canceled() : scan(text);
            case TYPE_MANUAL_INPUT:
                return manualInput();
            default:
                return canceled();
        }
    }

    @Type
    public int getType() {
        return type;
    }

    @NonNull
    public List<String> getPhotoPaths() {
        return photoPaths;
    }

    @Nullable
    public String getScanText() {
        return scanText;
    }

    public boolean isCanceled() {
        return type == TYPE_CANCELED;
    }
}
