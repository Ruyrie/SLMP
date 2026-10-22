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
 * 多媒体临时文件目录：拍照与录像 cacheDir/capture、图片压缩 cacheDir/compress、相册选择的副本 cacheDir/pick、
 * 上传队列的压缩与转码结果 cacheDir/upload（按任务 id 命名，Worker 重试时可复用）；超过 24 小时的文件在启动时清理。
 */
public final class MediaFiles {

    public static final String DIR_CAPTURE = "capture";
    public static final String DIR_COMPRESS = "compress";
    public static final String DIR_PICK = "pick";
    public static final String DIR_UPLOAD = "upload";

    private static final String[] OWN_DIRS = {DIR_CAPTURE, DIR_COMPRESS, DIR_PICK, DIR_UPLOAD};

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

    /** 录像输出文件：cacheDir/capture/VID_20261019_091000_123.mp4 */
    @NonNull
    public static File newVideoCaptureFile(@NonNull Context context) {
        String name = new SimpleDateFormat("'VID_'yyyyMMdd_HHmmss_SSS'.mp4'", Locale.ROOT).format(new Date());
        return new File(dir(context, DIR_CAPTURE), name);
    }

    /** 压缩输出文件：cacheDir/compress/&lt;uuid&gt;.jpg */
    @NonNull
    public static File newCompressFile(@NonNull Context context) {
        return new File(dir(context, DIR_COMPRESS), UUID.randomUUID().toString().replace("-", "") + ".jpg");
    }

    /** 相册选择的副本：cacheDir/pick/&lt;uuid&gt;.&lt;ext&gt;（照片选择器的临时授权在进程结束后失效，先复制） */
    @NonNull
    public static File newPickFile(@NonNull Context context, @NonNull String ext) {
        return new File(dir(context, DIR_PICK), UUID.randomUUID().toString().replace("-", "") + "." + ext);
    }

    /** 上传任务的压缩图片：cacheDir/upload/task_&lt;id&gt;.jpg */
    @NonNull
    public static File taskImageFile(@NonNull Context context, long taskId) {
        return new File(dir(context, DIR_UPLOAD), "task_" + taskId + ".jpg");
    }

    /** 上传任务的转码视频：cacheDir/upload/task_&lt;id&gt;.mp4 */
    @NonNull
    public static File taskVideoFile(@NonNull Context context, long taskId) {
        return new File(dir(context, DIR_UPLOAD), "task_" + taskId + ".mp4");
    }

    /** 上传任务的视频封面（第 0 秒帧）：cacheDir/upload/task_&lt;id&gt;_cover.jpg */
    @NonNull
    public static File taskCoverFile(@NonNull Context context, long taskId) {
        return new File(dir(context, DIR_UPLOAD), "task_" + taskId + "_cover.jpg");
    }

    /** 本组件生成的临时文件（只删除 cacheDir 下本组件目录中的文件） */
    public static boolean isOwnTempFile(@NonNull Context context, @Nullable String path) {
        if (path == null) {
            return false;
        }
        String cache = context.getCacheDir().getAbsolutePath();
        for (String d : OWN_DIRS) {
            if (path.startsWith(cache + File.separator + d + File.separator)) {
                return true;
            }
        }
        return false;
    }

    /** 删除本组件的临时文件；path 可以是文件路径或 file:// 地址 */
    public static void deleteTemp(@NonNull Context context, @Nullable String pathOrUri) {
        String path = toPath(pathOrUri);
        if (isOwnTempFile(context, path)) {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        }
    }

    /** 删除上传任务生成的压缩、转码与封面文件 */
    public static void deleteTaskFiles(@NonNull Context context, long taskId) {
        //noinspection ResultOfMethodCallIgnored
        taskImageFile(context, taskId).delete();
        //noinspection ResultOfMethodCallIgnored
        taskVideoFile(context, taskId).delete();
        //noinspection ResultOfMethodCallIgnored
        taskCoverFile(context, taskId).delete();
    }

    /** file:// 地址或绝对路径转为路径；其他（content://、http、asset）返回 null */
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

    /** 路径或地址转为 Uri（绝对路径转为 file://） */
    @NonNull
    public static Uri toUri(@NonNull String pathOrUri) {
        return pathOrUri.startsWith("/") ? Uri.fromFile(new File(pathOrUri)) : Uri.parse(pathOrUri);
    }

    /** 清理超过 24 小时的临时文件（io 线程） */
    @WorkerThread
    public static void cleanupExpired(@NonNull Context context) {
        long expire = System.currentTimeMillis() - KEEP_MS;
        for (String name : OWN_DIRS) {
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
