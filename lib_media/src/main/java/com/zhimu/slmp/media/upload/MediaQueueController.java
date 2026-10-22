package com.zhimu.slmp.media.upload;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;
import androidx.work.Data;
import androidx.work.WorkInfo;

import com.zhimu.slmp.base.db.entity.UploadTaskEntity;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.model.vo.FileVO;
import com.zhimu.slmp.media.process.MediaValidator;
import com.zhimu.slmp.media.queue.UploadQueue;
import com.zhimu.slmp.media.queue.UploadTaskStates;
import com.zhimu.slmp.media.util.MediaFiles;
import com.zhimu.slmp.media.work.MediaUploadWorker;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 照片和视频网格的上传管理（模块07 I02、I03、I05，03 号规范 8.4 节）：网格内容 = 带入的服务端文件（remote 项）+
 * 上传队列 upload_task 中本页面的任务。照片和视频合计最多 maxCount 个，视频最多 maxVideoCount 段；
 * 相机页拍摄的照片和视频由相机页直接写入队列（page_code 相同），网格观察数据库自动出现；
 * 压缩、转码、上传在 WorkManager 中进行（离开页面、锁屏也不中断），进度来自 upload_task 与 WorkInfo。
 * <pre>
 * // 宿主 ViewModel：构造方法注入 MediaQueueController.Factory media
 * &#64;Override protected void onSavedStateAttached(SavedStateHandle h) {
 *     grid = media.create(h, "media", MediaScene.INSPECTION, "I02", farmContext.farmIdOrZero(), 6, 1);
 * }
 * // 提交前：grid.hasUploading() 等待、grid.getSubmitBlockMessage() 提示；请求体 grid.getFileIds()
 * // 提交成功：grid.clearAfterSubmit()；onCleared：grid.release()
 * </pre>
 * 首次进入（不是页面重建）时清理本页面遗留的任务；进程被回收后从 SavedStateHandle 恢复带入项，队列任务照常继续。
 * 所有方法在主线程调用。
 */
public class MediaQueueController implements UploadController {

    public static final long UNDO_WINDOW_MS = 4000;

    public static final String MSG_FAILED_SUBMIT = "有 %d 个文件上传失败，请重试或删除后再提交";
    public static final String MSG_FULL = "已添加 %1$d / %1$d，删除后再添加";
    public static final String MSG_OVER_LIMIT = "最多 %1$d 个，已添加前 %2$d 个";
    public static final String MSG_IMAGE_TYPE = "只支持 JPG 和 PNG 图片";

    private static final String STATE_REMOTE = "remote";
    private static final String STATE_INITIALIZED = "initialized";

    /** 宿主 ViewModel 注入这个工厂（依赖都来自 CoreComponent，业务组件无需额外配置） */
    public static class Factory {
        private final UploadQueue queue;
        private final Context context;

        @Inject
        public Factory(@NonNull UploadQueue queue, @NonNull Context context) {
            this.queue = queue;
            this.context = context.getApplicationContext();
        }

        /**
         * @param key           本网格在 handle 中的键
         * @param scene         上传场景 MediaScene（INSPECTION、ABNORMAL 允许视频）
         * @param pageCode      上传队列的页面编号（同一页面的任务一起清理）
         * @param farmId        当前养殖场（任务按此养殖场上传）
         * @param maxCount      照片和视频合计最多几个
         * @param maxVideoCount 视频最多几段
         */
        @NonNull
        public MediaQueueController create(@NonNull SavedStateHandle handle, @NonNull String key,
                                           @NonNull String scene, @NonNull String pageCode, long farmId,
                                           int maxCount, int maxVideoCount) {
            return new MediaQueueController(queue, context, handle, key, scene, pageCode, farmId, maxCount,
                    maxVideoCount);
        }
    }

    /** 数据库任务与在 io 线程读到的本地文件信息 */
    static final class TaskSnapshot {
        final UploadTaskEntity task;
        @Nullable
        final String outputPath;
        @Nullable
        final String coverPath;
        @Nullable
        final Integer durationSec;

        TaskSnapshot(UploadTaskEntity task, @Nullable String outputPath, @Nullable String coverPath,
                     @Nullable Integer durationSec) {
            this.task = task;
            this.outputPath = outputPath;
            this.coverPath = coverPath;
            this.durationSec = durationSec;
        }
    }

    private final UploadQueue queue;
    private final Context context;
    private final String scene;
    private final String pageCode;
    private final long farmId;
    private final int maxCount;
    private final int maxVideoCount;

    private final MutableLiveData<List<UploadItem>> items = new MutableLiveData<>(Collections.emptyList());
    private final SingleLiveEvent<UiMessage> message = new SingleLiveEvent<>();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final List<UploadItem> remoteItems = new ArrayList<>();
    private List<TaskSnapshot> tasks = Collections.emptyList();
    private final Map<Long, Integer> progressOverride = new HashMap<>();
    private final Set<String> hidden = new HashSet<>();
    private final Map<Long, Integer> durationCache = new ConcurrentHashMap<>();
    private final LiveData<List<WorkInfo>> workInfos;
    private final Observer<List<WorkInfo>> workObserver = this::onWorkInfos;

    private int reserved;
    private boolean initialized;
    private boolean released;
    @Nullable
    private PendingRemoval pendingRemoval;

    MediaQueueController(@NonNull UploadQueue queue, @NonNull Context context, @NonNull SavedStateHandle handle,
                         @NonNull String key, @NonNull String scene, @NonNull String pageCode, long farmId,
                         int maxCount, int maxVideoCount) {
        this.queue = queue;
        this.context = context;
        this.scene = scene;
        this.pageCode = pageCode;
        this.farmId = farmId;
        this.maxCount = Math.max(1, maxCount);
        this.maxVideoCount = Math.max(0, maxVideoCount);

        boolean restored = restore(handle.get(key));
        handle.setSavedStateProvider(key, this::saveState);
        Completable start = restored ? Completable.complete()
                : queue.clearPage(pageCode, farmId, true).onErrorComplete();
        disposables.add(start.subscribeOn(Schedulers.io())
                .andThen(Flowable.defer(() -> queue.observe(pageCode, farmId)))
                .map(this::snapshot)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> {
                    tasks = list;
                    rebuild();
                }, e -> {
                }));
        workInfos = queue.workInfos(pageCode, farmId);
        workInfos.observeForever(workObserver);
        rebuild();
    }

    // ===== 初始内容 =====

    /** 带入服务端已有的文件（I05 带入巡检照片、修改时带入原记录的照片和视频）；只在第一次调用时生效 */
    @MainThread
    public void setInitialFiles(@Nullable List<? extends FileVO> files) {
        if (initialized) {
            return;
        }
        initialized = true;
        if (files != null) {
            for (FileVO vo : files) {
                remoteItems.add(UploadItem.remote(vo));
            }
        }
        rebuild();
    }

    // ===== UploadController =====

    @NonNull
    @Override
    public LiveData<List<UploadItem>> getItems() {
        return items;
    }

    @NonNull
    @Override
    public LiveData<UiMessage> getMessage() {
        return message;
    }

    @Override
    public int getMaxCount() {
        return maxCount;
    }

    @Override
    public int getMaxVideoCount() {
        return maxVideoCount;
    }

    @NonNull
    @Override
    public String getScene() {
        return scene;
    }

    @Nullable
    @Override
    public String getQueuePageCode() {
        return pageCode;
    }

    @Override
    public long getQueueFarmId() {
        return farmId;
    }

    @Override
    public int getRemaining() {
        return Math.max(0, maxCount - current().size() - reserved);
    }

    @Override
    public boolean hasVideo() {
        for (UploadItem item : current()) {
            if (item.isVideo()) {
                return true;
            }
        }
        return false;
    }

    /** 带入（不是本次新加）的项数，用于“前 3 个来自巡检” */
    public int getRemoteCount() {
        int n = 0;
        for (UploadItem item : current()) {
            if (item.isRemote()) {
                n++;
            }
        }
        return n;
    }

    @MainThread
    @Override
    public void addUris(@NonNull List<Uri> uris) {
        if (uris.isEmpty() || released) {
            return;
        }
        int remaining = getRemaining();
        if (remaining <= 0) {
            showMessage(String.format(Locale.ROOT, MSG_FULL, maxCount));
            return;
        }
        final boolean videoTaken = hasVideo();
        final int limit = remaining;
        reserved += Math.min(uris.size(), limit);
        final int reservedNow = Math.min(uris.size(), limit);
        final List<Uri> input = new ArrayList<>(uris);
        disposables.add(Single.fromCallable(() -> classify(input, limit, videoTaken))
                .flatMap(r -> {
                    Completable c = Completable.complete();
                    for (Object[] a : r.accepted) {
                        c = c.andThen(queue.enqueuePicked((Uri) a[0], (String) a[1], scene, pageCode, farmId)
                                .ignoreElement().onErrorComplete());
                    }
                    return c.toSingleDefault(r);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(r -> {
                    reserved -= reservedNow;
                    if (r.tooLong) {
                        showMessage(MediaValidator.MSG_VIDEO_TOO_LONG);
                    } else if (r.invalid) {
                        showMessage(MSG_IMAGE_TYPE);
                    } else if (r.extraVideo) {
                        showMessage(videoTaken ? MediaValidator.MSG_HAS_VIDEO : MediaValidator.MSG_ONE_VIDEO);
                    } else if (r.truncated) {
                        showMessage(String.format(Locale.ROOT, MSG_OVER_LIMIT, maxCount, r.accepted.size()));
                    }
                    rebuild();
                }, e -> {
                    reserved -= reservedNow;
                    rebuild();
                }));
        rebuild();
    }

    /** 选择结果的分类（io 线程） */
    private static final class Classified {
        final List<Object[]> accepted = new ArrayList<>();
        boolean invalid;
        boolean tooLong;
        boolean extraVideo;
        boolean truncated;
    }

    @NonNull
    private Classified classify(@NonNull List<Uri> uris, int limit, boolean videoTaken) {
        Classified r = new Classified();
        int videos = videoTaken ? 1 : 0;
        for (Uri uri : uris) {
            if (r.accepted.size() >= limit) {
                r.truncated = true;
                break;
            }
            String type = MediaValidator.detectMediaType(context, uri);
            if (type == null) {
                r.invalid = true;
                continue;
            }
            if (UploadItem.MEDIA_VIDEO.equals(type)) {
                if (videos >= maxVideoCount) {
                    r.extraVideo = true;
                    continue;
                }
                MediaValidator.VideoInfo info = MediaValidator.readVideoInfo(context, uri.toString());
                if (info == null || !MediaValidator.isDurationAllowed(info.durationMs)) {
                    r.tooLong = info != null;
                    r.invalid = info == null;
                    continue;
                }
                videos++;
            }
            r.accepted.add(new Object[]{uri, type});
        }
        return r;
    }

    @MainThread
    @Override
    public void addCapturedFiles(@NonNull List<String> paths) {
        if (paths.isEmpty() || released) {
            return;
        }
        int remaining = getRemaining();
        Completable c = Completable.complete();
        int added = 0;
        for (String path : paths) {
            if (added >= remaining) {
                MediaFiles.deleteTemp(context, path);
                continue;
            }
            added++;
            c = c.andThen(queue.enqueue(path, UploadItem.MEDIA_IMAGE, scene, pageCode, farmId).ignoreElement());
        }
        if (paths.size() > remaining) {
            showMessage(String.format(Locale.ROOT, MSG_OVER_LIMIT, maxCount, remaining));
        }
        disposables.add(c.subscribeOn(Schedulers.io()).onErrorComplete().subscribe());
    }

    @MainThread
    @Override
    public void retry(@NonNull String itemId) {
        UploadItem item = find(itemId);
        if (item == null || !item.isFailed() || item.getTaskId() < 0 || released) {
            return;
        }
        disposables.add(queue.retry(item.getTaskId()).subscribeOn(Schedulers.io()).onErrorComplete().subscribe());
    }

    @MainThread
    @Override
    public void remove(@NonNull String itemId) {
        commitPendingRemoval();
        List<UploadItem> list = current();
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(itemId)) {
                index = i;
            }
        }
        if (index < 0) {
            return;
        }
        UploadItem item = list.get(index);
        hidden.add(itemId);
        rebuild();
        Disposable timer = Completable.timer(UNDO_WINDOW_MS, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .subscribe(this::commitPendingRemoval);
        pendingRemoval = new PendingRemoval(item, timer);
    }

    @MainThread
    @Override
    public void undoRemove() {
        PendingRemoval pending = pendingRemoval;
        if (pending == null || released) {
            return;
        }
        if (current().size() + reserved >= maxCount) {
            commitPendingRemoval();
            return;
        }
        pending.timer.dispose();
        pendingRemoval = null;
        hidden.remove(pending.item.getId());
        rebuild();
    }

    @NonNull
    @Override
    public List<Long> getFileIds() {
        List<Long> ids = new ArrayList<>();
        for (UploadItem item : current()) {
            if (item.isDone() && item.getFileId() > 0) {
                ids.add(item.getFileId());
            }
        }
        return ids;
    }

    @Override
    public boolean hasUploading() {
        if (reserved > 0) {
            return true;
        }
        for (UploadItem item : current()) {
            if (item.isInProgress()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasFailed() {
        return getFailedCount() > 0;
    }

    @Override
    public int getFailedCount() {
        int n = 0;
        for (UploadItem item : current()) {
            if (item.isFailed()) {
                n++;
            }
        }
        return n;
    }

    /** 有上传失败的项时返回“有 1 个文件上传失败，请重试或删除后再提交”，否则 null */
    @Nullable
    public String getSubmitBlockMessage() {
        int failed = getFailedCount();
        return failed > 0 ? String.format(Locale.ROOT, MSG_FAILED_SUBMIT, failed) : null;
    }

    /** 当前网格内容 */
    @NonNull
    public List<UploadItem> current() {
        List<UploadItem> list = items.getValue();
        return list == null ? Collections.emptyList() : list;
    }

    /** 业务记录提交成功后调用：删除本页面的任务记录（文件已被记录引用，不调用 API-U07） */
    @MainThread
    public void clearAfterSubmit() {
        PendingRemoval pending = pendingRemoval;
        pendingRemoval = null;
        if (pending != null) {
            pending.timer.dispose();
        }
        //noinspection ResultOfMethodCallIgnored
        queue.clearPage(pageCode, farmId, false).subscribeOn(Schedulers.io()).onErrorComplete().subscribe();
    }

    /** 宿主 ViewModel.onCleared 中调用：执行尚在撤销时限内的删除，停止观察（队列任务照常继续） */
    @MainThread
    public void release() {
        if (released) {
            return;
        }
        commitPendingRemoval();
        released = true;
        workInfos.removeObserver(workObserver);
        disposables.dispose();
    }

    // ===== 内部 =====

    @NonNull
    private List<TaskSnapshot> snapshot(@NonNull List<UploadTaskEntity> list) {
        List<TaskSnapshot> out = new ArrayList<>(list.size());
        for (UploadTaskEntity t : list) {
            File output = queue.outputFile(t);
            File cover = queue.coverFile(t);
            Integer duration = null;
            if (UploadItem.MEDIA_VIDEO.equals(t.mediaType)) {
                duration = durationCache.get(t.id);
                if (duration == null) {
                    String src = output != null ? output.getAbsolutePath() : t.localUri;
                    MediaValidator.VideoInfo info = MediaValidator.readVideoInfo(context, src);
                    if (info != null && info.durationMs > 0) {
                        duration = (int) Math.max(1, Math.round(info.durationMs / 1000.0));
                        durationCache.put(t.id, duration);
                    }
                }
            }
            out.add(new TaskSnapshot(t, output == null ? null : output.getAbsolutePath(),
                    cover == null ? null : cover.getAbsolutePath(), duration));
        }
        return out;
    }

    private void onWorkInfos(@Nullable List<WorkInfo> infos) {
        progressOverride.clear();
        if (infos != null) {
            for (WorkInfo info : infos) {
                if (info.getState() != WorkInfo.State.RUNNING) {
                    continue;
                }
                Data p = info.getProgress();
                long taskId = p.getLong(MediaUploadWorker.KEY_TASK_ID, -1);
                int progress = p.getInt(MediaUploadWorker.KEY_PROGRESS, -1);
                if (taskId > 0 && progress >= 0) {
                    progressOverride.put(taskId, progress);
                }
            }
        }
        rebuild();
    }

    private void rebuild() {
        if (released) {
            return;
        }
        List<UploadItem> list = new ArrayList<>();
        for (UploadItem item : remoteItems) {
            if (!hidden.contains(item.getId())) {
                list.add(item);
            }
        }
        for (TaskSnapshot s : tasks) {
            UploadItem item = toItem(s);
            if (!hidden.contains(item.getId())) {
                list.add(item);
            }
        }
        List<UploadItem> old = items.getValue();
        if (!list.equals(old)) {
            items.setValue(Collections.unmodifiableList(list));
        }
    }

    @NonNull
    UploadItem toItem(@NonNull TaskSnapshot s) {
        UploadTaskEntity t = s.task;
        UploadItem.Status status = UploadTaskStates.toItemStatus(t.status);
        int progress = t.progress;
        Integer live = progressOverride.get(t.id);
        if (live != null && UploadTaskStates.isActive(t.status)) {
            progress = live;
        }
        String source = t.localUri.startsWith("/") ? Uri.fromFile(new File(t.localUri)).toString() : t.localUri;
        return UploadItem.ofTask(t.id, status, progress, source, s.outputPath,
                t.fileId == null ? 0 : t.fileId, t.mediaType.isEmpty() ? UploadItem.MEDIA_IMAGE : t.mediaType,
                s.durationSec, s.coverPath, t.error);
    }

    private void commitPendingRemoval() {
        PendingRemoval pending = pendingRemoval;
        if (pending == null) {
            return;
        }
        pendingRemoval = null;
        pending.timer.dispose();
        UploadItem item = pending.item;
        if (item.getTaskId() >= 0) {
            // 不随页面取消：离开页面时仍删除已上传但未提交的文件
            //noinspection ResultOfMethodCallIgnored
            queue.remove(item.getTaskId(), true).subscribeOn(Schedulers.io()).onErrorComplete().subscribe();
        } else {
            remoteItems.remove(item);
            hidden.remove(item.getId());
            rebuild();
        }
    }

    @Nullable
    private UploadItem find(@NonNull String id) {
        for (UploadItem item : current()) {
            if (item.getId().equals(id)) {
                return item;
            }
        }
        return null;
    }

    private void showMessage(@NonNull String text) {
        message.setValue(UiMessage.of(text));
    }

    @NonNull
    private Bundle saveState() {
        Bundle b = new Bundle();
        b.putParcelableArrayList(STATE_REMOTE, new ArrayList<>(remoteItems));
        b.putBoolean(STATE_INITIALIZED, initialized);
        return b;
    }

    private boolean restore(@Nullable Object saved) {
        if (!(saved instanceof Bundle)) {
            return false;
        }
        Bundle b = (Bundle) saved;
        b.setClassLoader(UploadItem.class.getClassLoader());
        initialized = b.getBoolean(STATE_INITIALIZED);
        List<UploadItem> list = BundleCompat.getParcelableArrayList(b, STATE_REMOTE, UploadItem.class);
        if (list != null) {
            remoteItems.addAll(list);
        }
        return true;
    }

    private static final class PendingRemoval {
        final UploadItem item;
        final Disposable timer;

        PendingRemoval(UploadItem item, Disposable timer) {
            this.item = item;
            this.timer = timer;
        }
    }
}
