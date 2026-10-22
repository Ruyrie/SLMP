package com.zhimu.slmp.media.process;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.zhimu.slmp.media.compress.MediaFileException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频取帧：封面（第 0 秒帧，与视频一起随 API-U05 上传）与 AI 诊断用的关键帧（均匀取若干帧，模块11 也可复用）。
 * 阻塞方法，在 io 线程调用。
 */
public final class VideoFrameExtractor {

    /** 封面长边 */
    public static final int COVER_MAX_SIDE = 720;
    public static final int COVER_QUALITY = 85;

    private VideoFrameExtractor() {
    }

    /** 一帧：时间点与图像（调用方负责回收 Bitmap） */
    public static final class Frame {
        public final long timeMs;
        @NonNull
        public final Bitmap bitmap;

        Frame(long timeMs, @NonNull Bitmap bitmap) {
            this.timeMs = timeMs;
            this.bitmap = bitmap;
        }
    }

    /** 截取第 0 秒帧写成 JPEG（封面） */
    @WorkerThread
    @NonNull
    public static File extractCover(@NonNull Context context, @NonNull String source, @NonNull File out)
            throws MediaFileException {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            MediaValidator.setDataSource(context, r, source);
            Bitmap frame = r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    COVER_MAX_SIDE, COVER_MAX_SIDE);
            if (frame == null) {
                frame = r.getFrameAtTime(0);
            }
            if (frame == null) {
                throw new MediaFileException(MediaValidator.MSG_VIDEO_UNREADABLE);
            }
            try (OutputStream os = new FileOutputStream(out)) {
                frame.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, os);
            }
            frame.recycle();
            return out;
        } catch (IOException | RuntimeException e) {
            throw new MediaFileException(MediaValidator.MSG_VIDEO_UNREADABLE, e);
        } finally {
            release(r);
        }
    }

    /**
     * 均匀截取 count 帧（首帧、中间、接近结尾），长边不超过 maxSide；读不到的帧跳过。
     */
    @WorkerThread
    @NonNull
    public static List<Frame> extractKeyFrames(@NonNull Context context, @NonNull String source, int count,
                                               int maxSide) {
        List<Frame> frames = new ArrayList<>();
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            MediaValidator.setDataSource(context, r, source);
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long duration = d == null ? 0 : Long.parseLong(d.trim());
            for (long t : sampleTimes(duration, count)) {
                Bitmap b = r.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        maxSide, maxSide);
                if (b != null) {
                    frames.add(new Frame(t, b));
                }
            }
        } catch (IOException | RuntimeException e) {
            // 读不到时返回已取得的帧
        } finally {
            release(r);
        }
        return frames;
    }

    /** 取帧时间点（毫秒）：count 帧均匀分布在 [0, duration - 300]；时长未知时只取第 0 秒 */
    @VisibleForTesting
    @NonNull
    public static long[] sampleTimes(long durationMs, int count) {
        if (durationMs <= 0 || count <= 1) {
            return new long[]{0};
        }
        long end = Math.max(0, durationMs - 300);
        long[] out = new long[count];
        for (int i = 0; i < count; i++) {
            out[i] = end * i / (count - 1);
        }
        return out;
    }

    private static void release(@Nullable MediaMetadataRetriever r) {
        if (r == null) {
            return;
        }
        try {
            r.release();
        } catch (Exception ignore) {
            // ignore
        }
    }
}
