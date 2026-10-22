package com.zhimu.slmp.media.process;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.zhimu.slmp.media.compress.ImageCompressor;
import com.zhimu.slmp.media.upload.UploadItem;
import com.zhimu.slmp.media.util.MediaFiles;

import java.util.Locale;

/**
 * 照片和视频校验（模块07 文档 2.7.2，03 号规范 8.3 节）：只收 image/jpeg、image/png、video/mp4；
 * 视频用 MediaMetadataRetriever 读取时长、分辨率与旋转，超过 30 秒不加入；转码后仍超过 30MB 的视频上传失败。
 * 读取方法会访问文件，只能在 io 线程调用；判断规则为纯函数，便于单元测试。
 */
public final class MediaValidator {

    public static final String MIME_MP4 = "video/mp4";

    /** 视频时长上限（相册选择；相机录像在 30 秒时自动停止） */
    public static final long MAX_VIDEO_DURATION_MS = 30_000;
    /** 允许的误差：录满 30 秒的文件可能比 30 秒多几十毫秒 */
    static final long DURATION_TOLERANCE_MS = 500;
    /** 转码后视频大小上限 */
    public static final long MAX_VIDEO_BYTES = 30L * 1024 * 1024;
    /** 目标分辨率：短边 720 */
    public static final int TARGET_SHORT_SIDE = 720;

    public static final String MSG_VIDEO_TOO_LONG = "视频不能超过 30 秒，请重新选择";
    public static final String MSG_VIDEO_TOO_LARGE = "视频不能超过 30MB，请重新录制";
    public static final String MSG_VIDEO_UNREADABLE = "视频无法读取，请重新选择";
    public static final String MSG_VIDEO_TRANSCODE_FAILED = "视频压缩失败，请重新录制";
    public static final String MSG_TYPE_INVALID = "只支持 JPG 和 PNG 图片、MP4 视频";
    public static final String MSG_ONE_VIDEO = "最多添加 1 段视频，已保留第 1 段";
    public static final String MSG_HAS_VIDEO = "已有 1 段视频，删除后才能再添加";

    private MediaValidator() {
    }

    /** 视频信息 */
    public static final class VideoInfo {
        public final int width;
        public final int height;
        public final int rotation;
        public final long durationMs;
        @Nullable
        public final String videoMime;
        @Nullable
        public final String audioMime;

        public VideoInfo(int width, int height, int rotation, long durationMs, @Nullable String videoMime,
                         @Nullable String audioMime) {
            this.width = width;
            this.height = height;
            this.rotation = rotation;
            this.durationMs = durationMs;
            this.videoMime = videoMime;
            this.audioMime = audioMime;
        }

        /** 显示方向的宽（旋转 90 / 270 时交换） */
        public int displayWidth() {
            return rotation % 180 == 0 ? width : height;
        }

        public int displayHeight() {
            return rotation % 180 == 0 ? height : width;
        }
    }

    // ===== 类型 =====

    /** 判断媒体类型：IMAGE（JPG、PNG）、VIDEO（MP4），其他返回 null */
    @WorkerThread
    @Nullable
    public static String detectMediaType(@NonNull Context context, @NonNull Uri uri) {
        String mime = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            mime = context.getContentResolver().getType(uri);
        }
        if (mime == null) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (ext != null && !ext.isEmpty()) {
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
            }
        }
        return mediaTypeOfMime(mime);
    }

    /** MIME → IMAGE / VIDEO / null（纯函数） */
    @Nullable
    public static String mediaTypeOfMime(@Nullable String mime) {
        if (mime == null) {
            return null;
        }
        String m = mime.toLowerCase(Locale.ROOT);
        if (ImageCompressor.isSupportedMime(m)) {
            return UploadItem.MEDIA_IMAGE;
        }
        if (MIME_MP4.equals(m)) {
            return UploadItem.MEDIA_VIDEO;
        }
        return null;
    }

    // ===== 视频 =====

    /** 读取视频信息；无法读取返回 null */
    @WorkerThread
    @Nullable
    public static VideoInfo readVideoInfo(@NonNull Context context, @NonNull String pathOrUri) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            setDataSource(context, r, pathOrUri);
            int w = parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int h = parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int rot = parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            long d = parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            String[] tracks = readTrackMimes(context, pathOrUri);
            return new VideoInfo(w, h, rot, d, tracks[0], tracks[1]);
        } catch (RuntimeException | java.io.IOException e) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Exception ignore) {
                // ignore
            }
        }
    }

    /** {视频轨 MIME, 音频轨 MIME} */
    @NonNull
    private static String[] readTrackMimes(@NonNull Context context, @NonNull String pathOrUri) {
        String[] out = new String[2];
        MediaExtractor ex = new MediaExtractor();
        try {
            String path = MediaFiles.toPath(pathOrUri);
            if (path != null) {
                ex.setDataSource(path);
            } else if (pathOrUri.startsWith("file:///android_asset/")) {
                try (AssetFileDescriptor afd = context.getAssets().openFd(
                        pathOrUri.substring("file:///android_asset/".length()))) {
                    ex.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                }
            } else {
                ex.setDataSource(context, Uri.parse(pathOrUri), null);
            }
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null) {
                    continue;
                }
                if (mime.startsWith("video/") && out[0] == null) {
                    out[0] = mime;
                } else if (mime.startsWith("audio/") && out[1] == null) {
                    out[1] = mime;
                }
            }
        } catch (Exception e) {
            // 读不到轨道时按需要转码处理
        } finally {
            ex.release();
        }
        return out;
    }

    static void setDataSource(@NonNull Context context, @NonNull MediaMetadataRetriever r,
                              @NonNull String pathOrUri) throws java.io.IOException {
        String path = MediaFiles.toPath(pathOrUri);
        if (path != null) {
            r.setDataSource(path);
        } else if (pathOrUri.startsWith("file:///android_asset/")) {
            try (AssetFileDescriptor afd = context.getAssets().openFd(
                    pathOrUri.substring("file:///android_asset/".length()))) {
                r.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            }
        } else {
            r.setDataSource(context, Uri.parse(pathOrUri));
        }
    }

    /** 时长是否在 30 秒内（含 0.5 秒误差） */
    public static boolean isDurationAllowed(long durationMs) {
        return durationMs > 0 && durationMs <= MAX_VIDEO_DURATION_MS + DURATION_TOLERANCE_MS;
    }

    /**
     * 是否需要转码：短边超过 720、编码不是 H.264 + AAC（无音轨视为符合）、或大小超过 30MB（文档 2.7.2）。
     */
    public static boolean needsTranscode(@NonNull VideoInfo info, long sizeBytes) {
        int shortSide = Math.min(info.width, info.height);
        boolean h264 = "video/avc".equals(info.videoMime);
        boolean aac = info.audioMime == null || "audio/mp4a-latm".equals(info.audioMime);
        return shortSide <= 0 || shortSide > TARGET_SHORT_SIDE || !h264 || !aac || sizeBytes > MAX_VIDEO_BYTES;
    }

    /** 等比缩放到短边不超过 720 后的显示尺寸 {宽, 高}（取偶数，编码器要求） */
    @NonNull
    @VisibleForTesting
    public static int[] targetSize(int displayWidth, int displayHeight) {
        int shortSide = Math.min(displayWidth, displayHeight);
        if (shortSide <= TARGET_SHORT_SIDE || shortSide <= 0) {
            return new int[]{even(displayWidth), even(displayHeight)};
        }
        double s = TARGET_SHORT_SIDE / (double) shortSide;
        return new int[]{even((int) Math.round(displayWidth * s)), even((int) Math.round(displayHeight * s))};
    }

    private static int even(int v) {
        return Math.max(2, v - (v % 2));
    }

    private static int parseInt(@Nullable String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLong(@Nullable String s) {
        try {
            return s == null ? 0 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
