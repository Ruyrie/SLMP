package com.zhimu.slmp.media.util;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * 多媒体临时文件目录：拍照 cacheDir/capture、压缩 cacheDir/compress；超过 24 小时的文件在启动时清理。
 */
public final class MediaFiles {

    public static final String DIR_CAPTURE = "capture";
    public static final String DIR_COMPRESS = "compress";

    /** 临时文件保留时长（与服务端未关联文件的清理周期一致） */
    private static final long KEEP_MS = 24L * 60 * 60 * 1000;

    private MediaFiles() {
    }

    @NonNull
    public static File dir(@NonNull Context context, @NonNull String name) {
        File dir = new File(context.getCacheDir(), name);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** 拍照输出文件：cacheDir/capture/IMG_20261019_091000_123.jpg */
    @NonNull
    public static File newCaptureFile(@NonNull Context context) {
        String name = new SimpleDateFormat("'IMG_'yyyyMMdd_HHmmss_SSS'.jpg'", Locale.ROOT).format(new Date());
        return new File(dir(context, DIR_CAPTURE), name);
    }

    /** 压缩输出文件：cacheDir/compress/&lt;uuid&gt;.jpg */
    @NonNull
    public static File newCompressFile(@NonNull Context context) {
        return new File(dir(context, DIR_COMPRESS), UUID.randomUUID().toString().replace("-", "") + ".jpg");
    }

    /** 本组件生成的临时文件（只删除 cacheDir 下 capture、compress 中的文件） */
    public static boolean isOwnTempFile(@NonNull Context context, @Nullable String path) {
        if (path == null) {
            return false;
        }
        String cache = context.getCacheDir().getAbsolutePath();
        return path.startsWith(cache + File.separator + DIR_CAPTURE + File.separator)
                || path.startsWith(cache + File.separator + DIR_COMPRESS + File.separator);
    }

    /** 删除本组件的临时文件；path 可以是文件路径或 file:// 地址 */
    public static void deleteTemp(@NonNull Context context, @Nullable String pathOrUri) {
        String path = toPath(pathOrUri);
        if (isOwnTempFile(context, path)) {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        }
    }

    /** file:// 地址或绝对路径转为路径；其他（content://、http）返回 null */
    @Nullable
    public static String toPath(@Nullable String pathOrUri) {
        if (pathOrUri == null || pathOrUri.isEmpty()) {
            return null;
        }
        if (pathOrUri.startsWith("/")) {
            return pathOrUri;
        }
        if (pathOrUri.startsWith("file://") && !pathOrUri.startsWith("file:///android_asset/")) {
            return Uri.parse(pathOrUri).getPath();
        }
        return null;
    }

    /** 清理超过 24 小时的拍照与压缩文件（io 线程） */
    @WorkerThread
    public static void cleanupExpired(@NonNull Context context) {
        long expire = System.currentTimeMillis() - KEEP_MS;
        for (String name : new String[]{DIR_CAPTURE, DIR_COMPRESS}) {
            File[] files = new File(context.getCacheDir(), name).listFiles();
            if (files == null) {
                continue;
            }
            for (File f : files) {
                if (f.isFile() && f.lastModified() < expire) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
    }
}
