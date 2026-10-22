package com.zhimu.slmp.media.queue;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.zhimu.slmp.base.db.AppDatabase;
import com.zhimu.slmp.base.db.dao.UploadTaskDao;
import com.zhimu.slmp.base.db.entity.UploadTaskEntity;
import com.zhimu.slmp.media.data.MediaRepository;
import com.zhimu.slmp.media.upload.UploadItem;
import com.zhimu.slmp.media.util.MediaFiles;
import com.zhimu.slmp.media.work.MediaUploadWorker;
import com.zhimu.slmp.media.work.VideoTranscodeWorker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 照片和视频上传队列（03 号规范 8.4 节，模块07 文档 2.7.3）：每个待上传文件在 Room upload_task 中有一条记录，
 * 对应一个 WorkManager 唯一任务 upload_&lt;id&gt;：
 * <ul>
 *     <li>图片：{@link MediaUploadWorker}（联网约束）压缩后上传 API-U05</li>
 *     <li>视频：{@link VideoTranscodeWorker}（dataSync 前台任务，通知“正在处理视频 62%”）转码与截封面，
 *     再接 {@link MediaUploadWorker} 上传（带 cover 与 duration_ms）</li>
 *     <li>网络错误与 5xx 指数退避重试 3 次；任务带 page_code 与 farm_id 标签，切换养殖场后按原养殖场继续</li>
 * </ul>
 * 页面通过 {@link #observe} 观察数据库、{@link #workInfos} 观察 WorkInfo 进度。
 */
public class UploadQueue {

    /** 全部上传任务的标签 */
    public static final String TAG_ALL = "zmt_upload";
    /** 首次失败后的退避时长（之后指数增长） */
    public static final long BACKOFF_SEC = 10;

    private final Context context;
    private final UploadTaskDao dao;
    private final MediaRepository repository;

    @Inject
    public UploadQueue(@NonNull Context context, @NonNull AppDatabase database, @NonNull MediaRepository repository) {
        this.context = context.getApplicationContext();
        this.dao = database.uploadTaskDao();
        this.repository = repository;
    }

    @NonNull
    public static String workName(long taskId) {
        return "upload_" + taskId;
    }

    @NonNull
    public static String pageTag(@NonNull String pageCode, long farmId) {
        return TAG_ALL + "_page_" + pageCode + "_" + farmId;
    }

    @NonNull
    public static String taskTag(long taskId) {
        return TAG_ALL + "_task_" + taskId;
    }

    /** 幂等键（同一任务重试时不变） */
    @NonNull
    public static String idemKey(@NonNull UploadTaskEntity task) {
        return "upload-" + task.id + "-" + task.createdAt;
    }

    @NonNull
    private WorkManager workManager() {
        return WorkManager.getInstance(context);
    }

    // ===== 入队 =====

    /**
     * 拍摄或已复制到本地的文件入队（io 线程订阅）。
     *
     * @param localUri  文件路径或 file:// 地址
     * @param mediaType {@link UploadItem#MEDIA_IMAGE} / {@link UploadItem#MEDIA_VIDEO}
     * @return upload_task.id
     */
    @NonNull
    public Single<Long> enqueue(@NonNull String localUri, @NonNull String mediaType, @NonNull String scene,
                                @NonNull String pageCode, long farmId) {
        return Single.defer(() -> {
            UploadTaskEntity task = new UploadTaskEntity();
            task.localUri = localUri;
            task.mediaType = mediaType;
            task.scene = scene;
            task.status = UploadTaskStates.WAITING;
            task.pageCode = pageCode;
            task.farmId = farmId;
            task.createdAt = System.currentTimeMillis();
            return dao.insert(task).doOnSuccess(id -> schedule(id, mediaType, pageCode, farmId));
        });
    }

    /** 相册选择的 content:// 先复制到 cacheDir/pick（照片选择器的授权在进程结束后失效），再入队 */
    @NonNull
    public Single<Long> enqueuePicked(@NonNull Uri uri, @NonNull String mediaType, @NonNull String scene,
                                      @NonNull String pageCode, long farmId) {
        return Single.fromCallable(() -> copyToPick(uri, mediaType))
                .flatMap(file -> enqueue(file.getAbsolutePath(), mediaType, scene, pageCode, farmId));
    }

    @WorkerThread
    @NonNull
    private File copyToPick(@NonNull Uri uri, @NonNull String mediaType) throws IOException {
        String ext = UploadItem.MEDIA_VIDEO.equals(mediaType) ? "mp4" : "jpg";
        String mime = context.getContentResolver().getType(uri);
        if (mime != null) {
            String e = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.toLowerCase(Locale.ROOT));
            if (e != null) {
                ext = e;
            }
        }
        File out = MediaFiles.newPickFile(context, ext);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) {
                throw new IOException("无法打开 " + uri);
            }
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        }
        return out;
    }

    /** 安排（或以 REPLACE 重新安排）任务的 Worker */
    public void schedule(long taskId, @NonNull String mediaType, @NonNull String pageCode, long farmId) {
        Data input = new Data.Builder().putLong(MediaUploadWorker.KEY_TASK_ID, taskId).build();
        String page = pageTag(pageCode, farmId);
        OneTimeWorkRequest upload = new OneTimeWorkRequest.Builder(MediaUploadWorker.class)
                .setInputData(input)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SEC, TimeUnit.SECONDS)
                .addTag(TAG_ALL)
                .addTag(page)
                .addTag(taskTag(taskId))
                .build();
        if (UploadItem.MEDIA_VIDEO.equals(mediaType)) {
            OneTimeWorkRequest transcode = new OneTimeWorkRequest.Builder(VideoTranscodeWorker.class)
                    .setInputData(input)
                    .addTag(TAG_ALL)
                    .addTag(page)
                    .addTag(taskTag(taskId))
                    .build();
            workManager().beginUniqueWork(workName(taskId), ExistingWorkPolicy.REPLACE, transcode)
                    .then(upload)
                    .enqueue();
        } else {
            workManager().enqueueUniqueWork(workName(taskId), ExistingWorkPolicy.REPLACE, upload);
        }
    }

    // ===== 操作 =====

    /** 失败项重试：FAILED → WAITING，以 REPLACE 重新入队 */
    @NonNull
    public Completable retry(long taskId) {
        return dao.get(taskId)
                .flatMapCompletable(task -> {
                    if (!UploadTaskStates.FAILED.equals(task.status)) {
                        return Completable.complete();
                    }
                    task.status = UploadTaskStates.WAITING;
                    task.progress = 0;
                    task.error = null;
                    return dao.update(task).doOnComplete(() ->
                            schedule(task.id, task.mediaType, task.pageCode, task.farmId));
                });
    }

    /**
     * 删除一个任务：取消 Worker、删除记录与本地文件；deleteRemote 为真且已上传时调用 API-U07（失败忽略）。
     */
    @NonNull
    public Completable remove(long taskId, boolean deleteRemote) {
        return Completable.fromAction(() -> workManager().cancelUniqueWork(workName(taskId)))
                .andThen(dao.get(taskId)
                        .flatMapCompletable(task -> dao.delete(taskId).andThen(cleanup(task, deleteRemote))));
    }

    /**
     * 清空某页面的任务：提交成功后（文件已被记录引用，deleteRemote 为 false）、进入表单时清理遗留任务
     * （未被引用，deleteRemote 为 true）。
     */
    @NonNull
    public Completable clearPage(@NonNull String pageCode, long farmId, boolean deleteRemote) {
        return Completable.fromAction(() -> workManager().cancelAllWorkByTag(pageTag(pageCode, farmId)))
                .andThen(dao.observeByPage(pageCode, farmId).firstOrError())
                .flatMapCompletable(list -> {
                    Completable c = dao.deleteByPage(pageCode, farmId);
                    for (UploadTaskEntity task : list) {
                        c = c.andThen(cleanup(task, deleteRemote));
                    }
                    return c;
                });
    }

    @NonNull
    private Completable cleanup(@NonNull UploadTaskEntity task, boolean deleteRemote) {
        Completable files = Completable.fromAction(() -> {
            MediaFiles.deleteTaskFiles(context, task.id);
            MediaFiles.deleteTemp(context, task.localUri);
        });
        if (deleteRemote && task.fileId != null && task.fileId > 0) {
            return files.andThen(repository.deleteFile(task.fileId).onErrorComplete());
        }
        return files;
    }

    // ===== 查询 =====

    /** 某页面的任务（按加入顺序），数据变化时重新发射 */
    @NonNull
    public Flowable<List<UploadTaskEntity>> observe(@NonNull String pageCode, long farmId) {
        return dao.observeByPage(pageCode, farmId).subscribeOn(Schedulers.io());
    }

    @NonNull
    public Maybe<UploadTaskEntity> get(long taskId) {
        return dao.get(taskId);
    }

    /** 某页面任务的 WorkInfo（转码、上传的细粒度进度） */
    @NonNull
    public LiveData<List<WorkInfo>> workInfos(@NonNull String pageCode, long farmId) {
        return workManager().getWorkInfosByTagLiveData(pageTag(pageCode, farmId));
    }

    /** 任务的本地成品文件：图片为压缩后的 JPEG，视频为转码后的 MP4；还没生成时为 null */
    @Nullable
    public File outputFile(@NonNull UploadTaskEntity task) {
        File f = UploadItem.MEDIA_VIDEO.equals(task.mediaType) ? MediaFiles.taskVideoFile(context, task.id)
                : MediaFiles.taskImageFile(context, task.id);
        return f.isFile() ? f : null;
    }

    /** 视频封面；没有时为 null */
    @Nullable
    public File coverFile(@NonNull UploadTaskEntity task) {
        File f = MediaFiles.taskCoverFile(context, task.id);
        return f.isFile() ? f : null;
    }
}
