package com.zhimu.slmp.media.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.zhimu.slmp.base.db.dao.UploadTaskDao;
import com.zhimu.slmp.base.db.entity.UploadTaskEntity;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.FileVO;
import com.zhimu.slmp.media.compress.ImageCompressor;
import com.zhimu.slmp.media.compress.MediaFileException;
import com.zhimu.slmp.media.data.MediaRepository;
import com.zhimu.slmp.media.di.MediaInjector;
import com.zhimu.slmp.media.process.MediaValidator;
import com.zhimu.slmp.media.queue.UploadQueue;
import com.zhimu.slmp.media.queue.UploadTaskStates;
import com.zhimu.slmp.media.upload.UploadItem;
import com.zhimu.slmp.media.util.MediaFiles;

import java.io.File;
import java.io.IOException;

/**
 * 上传任务（上传队列最后一步）：图片先压缩（长边 1920、JPEG 85、不超过 5MB），视频使用 {@link VideoTranscodeWorker}
 * 的输出；调用 API-U05（ProgressRequestBody 报进度，X-Farm-Id 取任务 farm_id，幂等键为任务 id）。
 * 网络错误与 5xx 交给 WorkManager 指数退避重试 3 次，40002、40003 直接失败（{@link UploadTaskStates#decide}）。
 */
public class MediaUploadWorker extends Worker {

    public static final String KEY_TASK_ID = "task_id";
    public static final String KEY_PROGRESS = "progress";
    public static final String KEY_STAGE = "stage";
    public static final String KEY_DURATION_MS = "duration_ms";
    public static final String KEY_FILE_ID = "file_id";

    private static final String TAG = "ZmtUpload";

    public MediaUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        long taskId = getInputData().getLong(KEY_TASK_ID, -1);
        Context ctx = getApplicationContext();
        UploadTaskDao dao = CoreInjector.get(ctx).database().uploadTaskDao();
        UploadTaskEntity task = dao.get(taskId).blockingGet();
        if (task == null) {
            return Result.failure();
        }
        if (UploadTaskStates.READY.equals(task.status)) {
            return Result.success();
        }
        MediaRepository repository = MediaInjector.component().mediaRepository();
        boolean video = UploadItem.MEDIA_VIDEO.equals(task.mediaType);
        File file;
        File cover = null;
        Long durationMs = null;
        String mime;
        try {
            if (video) {
                file = MediaFiles.taskVideoFile(ctx, taskId);
                if (!file.isFile()) {
                    throw new MediaFileException(MediaValidator.MSG_VIDEO_TRANSCODE_FAILED);
                }
                File c = MediaFiles.taskCoverFile(ctx, taskId);
                cover = c.isFile() ? c : null;
                long d = getInputData().getLong(KEY_DURATION_MS, 0);
                if (d <= 0) {
                    MediaValidator.VideoInfo info = MediaValidator.readVideoInfo(ctx, file.getAbsolutePath());
                    d = info == null ? 0 : info.durationMs;
                }
                durationMs = d > 0 ? d : null;
                mime = MediaValidator.MIME_MP4;
            } else {
                file = MediaFiles.taskImageFile(ctx, taskId);
                if (!file.isFile()) {
                    dao.updateProgress(taskId, UploadTaskStates.COMPRESSING, 0).blockingAwait();
                    File compressed = new ImageCompressor(ctx).compress(MediaFiles.toUri(task.localUri));
                    if (!compressed.renameTo(file)) {
                        file = compressed;
                    }
                }
                mime = ImageCompressor.MIME_JPEG;
            }
        } catch (MediaFileException e) {
            fail(dao, taskId, e.getMessage());
            return Result.failure();
        } catch (IOException | RuntimeException e) {
            fail(dao, taskId, MediaFileException.MSG_UNREADABLE);
            return Result.failure();
        }

        dao.updateProgress(taskId, UploadTaskStates.UPLOADING, 0).blockingAwait();
        final int[] last = {0};
        try {
            FileVO vo = repository.uploadMedia(file, mime, task.scene, UploadQueue.idemKey(task),
                    (written, total, percent) -> {
                        setProgressAsync(new Data.Builder()
                                .putLong(KEY_TASK_ID, taskId)
                                .putInt(KEY_PROGRESS, percent)
                                .putString(KEY_STAGE, UploadTaskStates.UPLOADING)
                                .build());
                        if (UploadTaskStates.shouldWriteProgress(last[0], percent)) {
                            last[0] = percent;
                            dao.updateProgress(taskId, UploadTaskStates.UPLOADING, Math.min(99, percent))
                                    .onErrorComplete().blockingAwait();
                        }
                    }, cover, durationMs, null, task.farmId).blockingGet();
            UploadTaskEntity t = dao.get(taskId).blockingGet();
            if (t == null) {
                // 上传过程中被删除：已上传的文件由删除方调用 API-U07
                return Result.failure();
            }
            t.status = UploadTaskStates.READY;
            t.progress = 100;
            t.fileId = vo.getFileId();
            t.error = null;
            dao.update(t).blockingAwait();
            return Result.success(new Data.Builder().putLong(KEY_TASK_ID, taskId)
                    .putLong(KEY_FILE_ID, vo.getFileId()).build());
        } catch (RuntimeException e) {
            if (isStopped()) {
                return Result.failure();
            }
            ApiException ae = ApiException.from(e);
            UploadTaskStates.ErrorDecision decision = UploadTaskStates.decide(ae.getCode(), getRunAttemptCount());
            Log.w(TAG, "上传失败 task=" + taskId + " code=" + ae.getCode() + " attempt=" + getRunAttemptCount()
                    + " → " + decision);
            if (decision == UploadTaskStates.ErrorDecision.RETRY) {
                dao.updateProgress(taskId, UploadTaskStates.WAITING, 0).onErrorComplete().blockingAwait();
                return Result.retry();
            }
            fail(dao, taskId, UploadTaskStates.failureText(ae.getCode(), ae.getMessage()));
            return Result.failure();
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
}
