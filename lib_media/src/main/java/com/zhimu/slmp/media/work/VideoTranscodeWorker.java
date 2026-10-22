package com.zhimu.slmp.media.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.zhimu.slmp.base.db.dao.UploadTaskDao;
import com.zhimu.slmp.base.db.entity.UploadTaskEntity;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.media.compress.MediaFileException;
import com.zhimu.slmp.media.process.MediaValidator;
import com.zhimu.slmp.media.process.VideoFrameExtractor;
import com.zhimu.slmp.media.process.VideoTranscoder;
import com.zhimu.slmp.media.queue.UploadTaskStates;
import com.zhimu.slmp.media.util.MediaFiles;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 视频处理任务（上传队列第一步，模块07 文档 2.7.2、2.7.3）：以 dataSync 前台服务运行（通知“正在处理视频 62%”，
 * 锁屏不中断），校验时长（30 秒内）→ 需要时用 Media3 Transformer 转为 720p H.264 + AAC → 检查大小（30MB 内）→
 * 截取第 0 秒帧作封面。输出 cacheDir/upload/task_&lt;id&gt;.mp4 与 _cover.jpg，成功后由 {@link MediaUploadWorker} 上传。
 * 进度写入 upload_task（节流）与 WorkInfo。失败直接标记 FAILED（转码失败不重试）。
 */
public class VideoTranscodeWorker extends Worker {

    private static final String TAG = "ZmtUpload";

    public VideoTranscodeWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        long taskId = getInputData().getLong(MediaUploadWorker.KEY_TASK_ID, -1);
        UploadTaskDao dao = CoreInjector.get(getApplicationContext()).database().uploadTaskDao();
        UploadTaskEntity task = dao.get(taskId).blockingGet();
        if (task == null) {
            return Result.failure();
        }
        if (UploadTaskStates.READY.equals(task.status)) {
            return Result.success(getInputData());
        }
        Context ctx = getApplicationContext();
        startForeground(taskId, 0);
        writeProgress(dao, taskId, 0);

        File out = MediaFiles.taskVideoFile(ctx, taskId);
        File cover = MediaFiles.taskCoverFile(ctx, taskId);
        try {
            MediaValidator.VideoInfo info = MediaValidator.readVideoInfo(ctx, task.localUri);
            if (info == null) {
                throw new MediaFileException(MediaValidator.MSG_VIDEO_UNREADABLE);
            }
            if (!MediaValidator.isDurationAllowed(info.durationMs)) {
                throw new MediaFileException(MediaValidator.MSG_VIDEO_TOO_LONG);
            }
            String path = MediaFiles.toPath(task.localUri);
            long size = path == null ? 0 : new File(path).length();
            if (!out.isFile() || out.length() == 0) {
                if (MediaValidator.needsTranscode(info, size)) {
                    final int[] last = {0};
                    VideoTranscoder.transcode(ctx, task.localUri, out, percent -> {
                        // 转码占处理进度的 95%，截封面占最后 5%
                        int p = Math.min(95, percent * 95 / 100);
                        setProgressAsync(progressData(taskId, p));
                        if (UploadTaskStates.shouldWriteProgress(last[0], p)) {
                            last[0] = p;
                            writeProgress(dao, taskId, p);
                            startForeground(taskId, p);
                        }
                    }, this::isStopped);
                } else {
                    copy(ctx, task.localUri, out);
                }
            }
            if (out.length() > MediaValidator.MAX_VIDEO_BYTES) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
                throw new MediaFileException(MediaValidator.MSG_VIDEO_TOO_LARGE);
            }
            try {
                VideoFrameExtractor.extractCover(ctx, out.getAbsolutePath(), cover);
            } catch (MediaFileException e) {
                Log.w(TAG, "封面截取失败 task=" + taskId);
            }
            writeProgress(dao, taskId, 100);
            setProgressAsync(progressData(taskId, 100));
            return Result.success(new Data.Builder()
                    .putLong(MediaUploadWorker.KEY_TASK_ID, taskId)
                    .putLong(MediaUploadWorker.KEY_DURATION_MS, info.durationMs)
                    .build());
        } catch (MediaFileException e) {
            if (isStopped()) {
                return Result.failure();
            }
            fail(dao, taskId, e.getMessage());
            return Result.failure();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure();
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "视频处理失败 task=" + taskId, e);
            fail(dao, taskId, MediaValidator.MSG_VIDEO_TRANSCODE_FAILED);
            return Result.failure();
        }
    }

    private void startForeground(long taskId, int percent) {
        try {
            ForegroundInfo info = UploadNotifications.foregroundInfo(getApplicationContext(), taskId, percent);
            setForegroundAsync(info).get();
        } catch (Exception e) {
            // 后台无法启动前台服务（Android 12+ 限制）时照常处理，只是没有通知
            Log.w(TAG, "前台通知启动失败 task=" + taskId + "：" + e.getClass().getSimpleName());
        }
    }

    private static void writeProgress(@NonNull UploadTaskDao dao, long taskId, int progress) {
        try {
            dao.updateProgress(taskId, UploadTaskStates.COMPRESSING, progress).blockingAwait();
        } catch (RuntimeException ignore) {
            // 任务已删除
        }
    }

    private static void fail(@NonNull UploadTaskDao dao, long taskId, @Nullable String reason) {
        UploadTaskEntity t = dao.get(taskId).blockingGet();
        if (t == null) {
            return;
        }
        t.status = UploadTaskStates.FAILED;
        t.progress = 0;
        t.error = reason;
        dao.update(t).blockingAwait();
    }

    @NonNull
    static Data progressData(long taskId, int progress) {
        return new Data.Builder()
                .putLong(MediaUploadWorker.KEY_TASK_ID, taskId)
                .putInt(MediaUploadWorker.KEY_PROGRESS, progress)
                .putString(MediaUploadWorker.KEY_STAGE, UploadTaskStates.COMPRESSING)
                .build();
    }

    private static void copy(@NonNull Context ctx, @NonNull String source, @NonNull File out) throws IOException {
        String path = MediaFiles.toPath(source);
        try (InputStream in = path != null ? new FileInputStream(path)
                : ctx.getContentResolver().openInputStream(MediaFiles.toUri(source));
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) {
                throw new IOException("无法读取视频");
            }
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        }
    }
}
