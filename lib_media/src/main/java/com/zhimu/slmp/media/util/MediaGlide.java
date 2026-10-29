package com.zhimu.slmp.media.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.zhimu.slmp.base.glide.FileGlideUrl;
import com.zhimu.slmp.media.upload.UploadItem;

import java.io.File;

/**
 * Glide 加载模型：服务端 http(s) 临时地址按 file_id 缓存（{@link FileGlideUrl}，原图键 file_&lt;id&gt;、缩略图键
 * file_&lt;id&gt;_thumb）；本地文件、content:// 与 file:///android_asset/（Mock 示例图）原样交给 Glide。
 * <p>
 * 注意：FileGlideUrl 走 HttpUrlFetcher，只能用于 http(s) 地址，非 http 地址包装后会加载失败。
 */
public final class MediaGlide {

    private MediaGlide() {
    }

    /**
     * 列表回收时清除图片加载。页面关闭时 RecyclerView 结束条目动画会在 Activity 已销毁后回收条目，
     * 此时 Glide.with 会抛“You cannot start a load for a destroyed activity”；页面销毁时 Glide 已释放其请求，直接跳过。
     */
    public static void clear(@NonNull View view) {
        Context ctx = view.getContext();
        while (ctx instanceof ContextWrapper && !(ctx instanceof Activity)) {
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        if (ctx instanceof Activity && ((Activity) ctx).isDestroyed()) {
            return;
        }
        Glide.with(view).clear(view);
    }

    /** 原图 */
    @Nullable
    public static Object model(@Nullable String url, long fileId) {
        return build(url, fileId > 0 ? "file_" + fileId : null);
    }

    /** 缩略图 */
    @Nullable
    public static Object thumbModel(@Nullable String thumbUrl, long fileId) {
        return build(thumbUrl, fileId > 0 ? "file_" + fileId + "_thumb" : null);
    }

    /** 网格缩略图：优先本地压缩文件（刚拍摄或选择的照片，无需联网），其次服务端缩略图、原图 */
    @Nullable
    public static Object gridModel(@NonNull UploadItem item) {
        // 视频优先用封面（本地 JPEG 或服务端缩略图）；没有封面时 Glide 从本地视频取帧
        if (item.isVideo() && item.getThumbUrl() != null) {
            return thumbModel(item.getThumbUrl(), item.isRemote() ? item.getFileId() : 0);
        }
        String local = item.getLocalPath();
        if (local != null && new File(local).isFile()) {
            return new File(local);
        }
        if (item.getSourceUri() != null && !item.isRemote()) {
            return Uri.parse(item.getSourceUri());
        }
        if (item.getThumbUrl() != null) {
            return thumbModel(item.getThumbUrl(), item.getFileId());
        }
        return model(item.getUrl(), item.getFileId());
    }

    @Nullable
    private static Object build(@Nullable String url, @Nullable String cacheKey) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        if (url.startsWith("/")) {
            return new File(url);
        }
        if (cacheKey != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            return new FileGlideUrl(url, cacheKey);
        }
        return url;
    }

    /** 是否为需要联网的服务端地址（加载失败时可调 API-U06 换地址） */
    public static boolean isRemoteUrl(@Nullable String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }
}
