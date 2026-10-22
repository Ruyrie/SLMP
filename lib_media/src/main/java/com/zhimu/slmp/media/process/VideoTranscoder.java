package com.zhimu.slmp.media.process;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.annotation.WorkerThread;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;

import com.google.common.collect.ImmutableList;
import com.zhimu.slmp.media.compress.MediaFileException;
import com.zhimu.slmp.media.util.MediaFiles;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * 视频压缩（03 号规范 8.3 节，模块07 文档 2.7.2）：Media3 Transformer 输出 H.264 + AAC 的 MP4，
 * Presentation 按短边 720 缩放，码率约 4Mbps；运行在调用线程自建的 HandlerThread 的 Looper 上，每 500 毫秒读一次进度。
 * 阻塞方法，在 Worker 的后台线程中调用。
 */
@OptIn(markerClass = UnstableApi.class)
public final class VideoTranscoder {

    public static final int VIDEO_BITRATE = 4_000_000;
    private static final long PROGRESS_INTERVAL_MS = 500;
    /** 30 秒视频在低端机上转码一般不超过 2 分钟，留足余量 */
    private static final long TIMEOUT_MIN = 10;

    /** 进度回调（0–100，后台线程） */
    public interface ProgressListener {
        void onProgress(int percent);
    }

    private VideoTranscoder() {
    }

    /**
     * 转码到 out（覆盖已有文件）。
     *
     * @param source 文件路径、file:// 或 content:// 地址
     * @throws MediaFileException 转码失败或被取消（message 可直接显示）
     */
    @WorkerThread
    @NonNull
    public static File transcode(@NonNull Context context, @NonNull String source, @NonNull File out,
                                 @NonNull ProgressListener listener) throws MediaFileException, InterruptedException {
        return transcode(context, source, out, listener, () -> false);
    }

    /**
     * @param cancelled 每 500 毫秒检查一次，返回 true 时取消转码（Worker 被停止、任务被删除）
     */
    @WorkerThread
    @NonNull
    public static File transcode(@NonNull Context context, @NonNull String source, @NonNull File out,
                                 @NonNull ProgressListener listener, @NonNull BooleanSupplier cancelled)
            throws MediaFileException, InterruptedException {
        Context app = context.getApplicationContext();
        //noinspection ResultOfMethodCallIgnored
        out.delete();
        HandlerThread thread = new HandlerThread("zmt-video-transcode");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<ExportException> error = new AtomicReference<>();
        AtomicReference<Transformer> ref = new AtomicReference<>();
        ProgressHolder holder = new ProgressHolder();

        Runnable poll = new Runnable() {
            @Override
            public void run() {
                Transformer t = ref.get();
                if (t == null || done.getCount() == 0) {
                    return;
                }
                if (cancelled.getAsBoolean()) {
                    t.cancel();
                    error.set(ExportException.createForUnexpected(new InterruptedException("cancelled")));
                    done.countDown();
                    return;
                }
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    listener.onProgress(holder.progress);
                }
                handler.postDelayed(this, PROGRESS_INTERVAL_MS);
            }
        };

        handler.post(() -> {
            try {
                Transformer transformer = new Transformer.Builder(app)
                        .setLooper(thread.getLooper())
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(new DefaultEncoderFactory.Builder(app)
                                .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder()
                                        .setBitrate(VIDEO_BITRATE)
                                        .build())
                                .build())
                        .addListener(new Transformer.Listener() {
                            @Override
                            public void onCompleted(@NonNull Composition composition, @NonNull ExportResult result) {
                                done.countDown();
                            }

                            @Override
                            public void onError(@NonNull Composition composition, @NonNull ExportResult result,
                                                @NonNull ExportException exception) {
                                error.set(exception);
                                done.countDown();
                            }
                        })
                        .build();
                ref.set(transformer);
                EditedMediaItem item = new EditedMediaItem.Builder(MediaItem.fromUri(MediaFiles.toUri(source)))
                        .setEffects(new Effects(ImmutableList.of(),
                                ImmutableList.of(Presentation.createForShortSide(MediaValidator.TARGET_SHORT_SIDE))))
                        .build();
                transformer.start(item, out.getAbsolutePath());
                handler.postDelayed(poll, PROGRESS_INTERVAL_MS);
            } catch (RuntimeException e) {
                error.set(ExportException.createForUnexpected(e));
                done.countDown();
            }
        });

        boolean finished;
        try {
            finished = done.await(TIMEOUT_MIN, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            cancel(handler, ref);
            thread.quitSafely();
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            throw e;
        }
        if (!finished) {
            cancel(handler, ref);
        }
        thread.quitSafely();
        if (!finished || error.get() != null || !out.isFile() || out.length() == 0) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            throw new MediaFileException(MediaValidator.MSG_VIDEO_TRANSCODE_FAILED,
                    error.get() != null ? error.get() : new IllegalStateException("transcode timeout"));
        }
        listener.onProgress(100);
        return out;
    }

    private static void cancel(@NonNull Handler handler, @NonNull AtomicReference<Transformer> ref) {
        CountDownLatch cancelled = new CountDownLatch(1);
        handler.post(() -> {
            Transformer t = ref.get();
            if (t != null) {
                t.cancel();
            }
            cancelled.countDown();
        });
        try {
            cancelled.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
    }
}
