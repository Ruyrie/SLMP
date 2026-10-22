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
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.model.vo.FileVO;
import com.zhimu.slmp.media.compress.ImageCompressor;
import com.zhimu.slmp.media.compress.MediaFileException;
import com.zhimu.slmp.media.data.MediaRepository;
import com.zhimu.slmp.media.util.MediaFiles;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 一个照片网格的上传管理（本期只有图片）：选中即校验 → 压缩（单线程排队，避免同时解码多张大图）→ API-U05 上传（带进度）。
 * <p>
 * 由宿主 ViewModel 持有并在 onCleared 中调用 {@link #release()}；网格内容通过宿主 ViewModel 的 SavedStateHandle
 * （{@code setSavedStateProvider}，键为 create 时传入的 key）保存：配置变化时 ViewModel 不销毁，上传继续；
 * 进程被回收后恢复列表，未完成的项重新排队（已压缩的不再压缩）。
 * <pre>
 * // 宿主 ViewModel
 * &#64;Inject XxxViewModel(XxxRepository repo, UploadManager.Factory uploads) { this.uploads = uploads; }
 * &#64;Override protected void onSavedStateAttached(SavedStateHandle h) {
 *     photos = uploads.create(h, "photos", MediaScene.DEATH, 6);
 * }
 * &#64;Override protected void onCleared() { photos.release(); super.onCleared(); }
 * </pre>
 * 所有方法在主线程调用。
 */
public class UploadManager implements UploadController {

    /** 删除后可撤销的时长（与 Snackbar 显示时长一致） */
    public static final long UNDO_WINDOW_MS = 4000;

    /** 02 号规范 10.1 节：提交时有上传失败的文件 */
    public static final String MSG_FAILED_SUBMIT = "有 %d 个文件上传失败，请重试或删除后再提交";
    /** 一次选择超过剩余数量（模块02 文档 2.7） */
    public static final String MSG_OVER_LIMIT = "最多 %1$d 张，已添加前 %2$d 张";
    /** 已满 */
    public static final String MSG_FULL = "最多添加 %d 张";

    private static final String STATE_ITEMS = "items";
    private static final String STATE_INITIALIZED = "initialized";
    private static final String MIME_UPLOAD = ImageCompressor.MIME_JPEG;

    /** 压缩排队：同一时刻只解码一张 */
    private static final Scheduler COMPRESS_SCHEDULER = Schedulers.from(
            Executors.newSingleThreadExecutor(r -> new Thread(r, "zmt-image-compress")));

    /** 宿主 ViewModel 注入这个工厂创建 UploadManager（依赖都来自 CoreComponent，业务组件无需额外配置） */
    public static class Factory {

        private final MediaRepository repository;
        private final ImageCompressor compressor;
        private final Context context;

        @Inject
        public Factory(@NonNull MediaRepository repository, @NonNull ImageCompressor compressor,
                       @NonNull Context context) {
            this.repository = repository;
            this.compressor = compressor;
            this.context = context.getApplicationContext();
        }

        /**
         * @param handle   宿主 ViewModel 的 SavedStateHandle
         * @param key      本网格在 handle 中的键（一个页面有多个网格时各不相同）
         * @param scene    上传场景 {@link com.zhimu.slmp.media.data.MediaScene}
         * @param maxCount 最多张数
         */
        @NonNull
        public UploadManager create(@NonNull SavedStateHandle handle, @NonNull String key, @NonNull String scene,
                                    int maxCount) {
            return new UploadManager(repository, compressor, context, handle, key, scene, maxCount);
        }
    }

    private final MediaRepository repository;
    private final ImageCompressor compressor;
    private final Context context;
    private final String scene;
    private final int maxCount;

    private final MutableLiveData<List<UploadItem>> items = new MutableLiveData<>(Collections.emptyList());
    private final SingleLiveEvent<UiMessage> message = new SingleLiveEvent<>();
    private final Map<String, Disposable> running = new HashMap<>();
    private final CompositeDisposable disposables = new CompositeDisposable();

    /** 正在校验类型、尚未加入网格的张数（计入已占用数量） */
    private int reserved;
    private boolean initialized;
    private boolean released;
    @Nullable
    private PendingRemoval pendingRemoval;
    @Nullable
    private String newItemTagText;
    @StatusTagView.TagType
    private int newItemTagType;

    UploadManager(@NonNull MediaRepository repository, @NonNull ImageCompressor compressor, @NonNull Context context,
                  @NonNull SavedStateHandle handle, @NonNull String key, @NonNull String scene, int maxCount) {
        this.repository = repository;
        this.compressor = compressor;
        this.context = context;
        this.scene = scene;
        this.maxCount = Math.max(1, maxCount);

        List<UploadItem> restored = restore(handle.get(key));
        handle.setSavedStateProvider(key, this::saveState);
        if (restored != null) {
            List<UploadItem> list = new ArrayList<>(restored.size());
            for (UploadItem item : restored) {
                list.add(item.toInterrupted());
            }
            items.setValue(Collections.unmodifiableList(list));
            for (UploadItem item : list) {
                if (item.isInProgress()) {
                    process(item);
                }
            }
        }
    }

    // ===== 初始内容 =====

    /**
     * 编辑模式带入服务端已有的文件（只在第一次调用时生效；页面重建、进程恢复后不覆盖用户的修改）。
     */
    @MainThread
    public void setInitialFiles(@Nullable List<? extends FileVO> files) {
        List<UploadItem> list = new ArrayList<>();
        if (files != null) {
            for (FileVO vo : files) {
                list.add(UploadItem.remote(vo));
            }
        }
        setInitialItems(list);
    }

    /** 同上，可先用 {@link UploadItem#withTag} 加“需重拍”等标签 */
    @MainThread
    public void setInitialItems(@NonNull List<UploadItem> remoteItems) {
        if (initialized) {
            return;
        }
        initialized = true;
        List<UploadItem> list = new ArrayList<>(remoteItems);
        list.addAll(current());
        items.setValue(Collections.unmodifiableList(list));
    }

    /** 新添加的项自动带上的标签，例如 F06 的“新增”（type 为 StatusTagView 的 TagType） */
    public void setNewItemTag(@Nullable String text, @StatusTagView.TagType int type) {
        this.newItemTagText = text;
        this.newItemTagType = type;
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

    @NonNull
    @Override
    public String getScene() {
        return scene;
    }

    @Override
    public int getRemaining() {
        return Math.max(0, maxCount - current().size() - reserved);
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
        final boolean truncated = uris.size() > remaining;
        final List<Uri> accepted = new ArrayList<>(truncated ? uris.subList(0, remaining) : uris);
        reserved += accepted.size();
        Disposable d = Single.fromCallable(() -> {
                    List<String> ok = new ArrayList<>();
                    for (Uri uri : accepted) {
                        if (ImageCompressor.isSupportedMime(compressor.detectMime(uri))) {
                            ok.add(uri.toString());
                        }
                    }
                    return ok;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ok -> {
                    reserved -= accepted.size();
                    addLocal(ok);
                    if (ok.size() < accepted.size()) {
                        showMessage(MediaFileException.MSG_IMAGE_INVALID);
                    } else if (truncated) {
                        showMessage(String.format(Locale.ROOT, MSG_OVER_LIMIT, maxCount, accepted.size()));
                    }
                }, e -> reserved -= accepted.size());
        disposables.add(d);
    }

    @MainThread
    @Override
    public void addCapturedFiles(@NonNull List<String> paths) {
        if (paths.isEmpty() || released) {
            return;
        }
        int remaining = getRemaining();
        List<String> sources = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            if (i < remaining) {
                sources.add(Uri.fromFile(new File(path)).toString());
            } else {
                deleteTempLater(path);
            }
        }
        if (paths.size() > remaining) {
            showMessage(String.format(Locale.ROOT, MSG_OVER_LIMIT, maxCount, remaining));
        }
        addLocal(sources);
    }

    @MainThread
    @Override
    public void retry(@NonNull String itemId) {
        UploadItem item = find(itemId);
        if (item == null || !item.isFailed() || released) {
            return;
        }
        if (update(itemId, UploadItem::toRetry)) {
            UploadItem next = find(itemId);
            if (next != null) {
                process(next);
            }
        }
    }

    @MainThread
    @Override
    public void remove(@NonNull String itemId) {
        commitPendingRemoval();
        List<UploadItem> list = new ArrayList<>(current());
        int index = indexOf(list, itemId);
        if (index < 0) {
            return;
        }
        UploadItem item = list.remove(index);
        Disposable task = running.remove(itemId);
        if (task != null) {
            task.dispose();
        }
        items.setValue(Collections.unmodifiableList(list));
        Disposable timer = Completable.timer(UNDO_WINDOW_MS, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .subscribe(this::commitPendingRemoval);
        pendingRemoval = new PendingRemoval(item, index, timer);
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
        UploadItem item = pending.item.toInterrupted();
        List<UploadItem> list = new ArrayList<>(current());
        list.add(Math.min(pending.index, list.size()), item);
        items.setValue(Collections.unmodifiableList(list));
        if (item.isInProgress()) {
            process(item);
        }
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

    /** 提交前检查：有失败项时返回“有 1 个文件上传失败，请重试或删除后再提交”，否则 null（上传中请先用 hasUploading 判断） */
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

    /** 宿主 ViewModel.onCleared 中调用：取消进行中的压缩与上传，执行尚在撤销时限内的删除 */
    @MainThread
    public void release() {
        if (released) {
            return;
        }
        commitPendingRemoval();
        released = true;
        for (Disposable d : running.values()) {
            d.dispose();
        }
        running.clear();
        disposables.dispose();
    }

    // ===== 处理流程 =====

    private void addLocal(@NonNull List<String> sources) {
        if (sources.isEmpty()) {
            return;
        }
        List<UploadItem> list = new ArrayList<>(current());
        List<UploadItem> added = new ArrayList<>();
        for (String src : sources) {
            UploadItem item = UploadItem.newLocal(src);
            if (newItemTagText != null) {
                item = item.withTag(newItemTagText, newItemTagType);
            }
            added.add(item);
        }
        list.addAll(added);
        items.setValue(Collections.unmodifiableList(list));
        for (UploadItem item : added) {
            process(item);
        }
    }

    /** WAITING 项：压缩（已压缩过则跳过）→ 上传 */
    private void process(@NonNull UploadItem item) {
        if (released) {
            return;
        }
        final String id = item.getId();
        Disposable old = running.remove(id);
        if (old != null) {
            old.dispose();
        }
        Single<File> fileStep;
        String local = item.getLocalPath();
        if (local != null && new File(local).isFile()) {
            fileStep = Single.just(new File(local));
        } else if (item.getSourceUri() != null) {
            final Uri source = Uri.parse(item.getSourceUri());
            fileStep = Single.fromCallable(() -> {
                AndroidSchedulers.mainThread().scheduleDirect(() -> update(id, UploadItem::toCompressing));
                try {
                    return compressor.compress(source);
                } catch (IOException e) {
                    throw new MediaFileException(MediaFileException.MSG_UNREADABLE, e);
                }
            }).subscribeOn(COMPRESS_SCHEDULER);
        } else {
            update(id, it -> it.toFailed(MediaFileException.MSG_UNREADABLE));
            return;
        }
        Disposable d = fileStep
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(file -> {
                    update(id, it -> it.toUploading(file.getAbsolutePath()));
                    return repository.upload(file, MIME_UPLOAD, scene, id,
                                    (written, total, percent) -> AndroidSchedulers.mainThread()
                                            .scheduleDirect(() -> update(id, it -> it.withProgress(percent))))
                            .subscribeOn(Schedulers.io());
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(vo -> {
                    running.remove(id);
                    update(id, it -> it.toDone(vo));
                }, e -> {
                    running.remove(id);
                    onFailed(id, e);
                });
        running.put(id, d);
    }

    private void onFailed(@NonNull String id, @NonNull Throwable e) {
        String reason;
        boolean notify;
        if (e instanceof MediaFileException) {
            reason = e.getMessage();
            notify = true;
        } else {
            ApiException ae = ApiException.from(e);
            reason = ae.getMessage();
            int code = ae.getCode();
            notify = code == ErrorCodes.FILE_INVALID || code == ErrorCodes.VIDEO_TOO_LARGE;
        }
        final String r = reason;
        update(id, it -> it.toFailed(r));
        if (notify && r != null) {
            showMessage(r);
        }
    }

    private void commitPendingRemoval() {
        PendingRemoval pending = pendingRemoval;
        if (pending == null) {
            return;
        }
        pendingRemoval = null;
        pending.timer.dispose();
        UploadItem item = pending.item;
        if (!item.isRemote() && item.getFileId() > 0) {
            // 不随页面取消：离开页面时仍删除已上传但未提交的文件
            //noinspection ResultOfMethodCallIgnored
            repository.deleteFile(item.getFileId())
                    .subscribeOn(Schedulers.io())
                    .onErrorComplete()
                    .subscribe();
        }
        if (!item.isRemote()) {
            deleteTempLater(item.getLocalPath());
            deleteTempLater(item.getSourceUri());
        }
    }

    private void deleteTempLater(@Nullable String pathOrUri) {
        if (pathOrUri != null) {
            Schedulers.io().scheduleDirect(() -> MediaFiles.deleteTemp(context, pathOrUri));
        }
    }

    /** 替换 id 对应的项；项已删除或状态变化不合法（例如上传完成后迟到的进度）时忽略 */
    private boolean update(@NonNull String id, @NonNull UnaryOperator<UploadItem> fn) {
        List<UploadItem> list = current();
        int index = indexOf(list, id);
        if (index < 0) {
            return false;
        }
        UploadItem old = list.get(index);
        UploadItem next;
        try {
            next = fn.apply(old);
        } catch (IllegalStateException e) {
            return false;
        }
        if (next != old) {
            List<UploadItem> copy = new ArrayList<>(list);
            copy.set(index, next);
            items.setValue(Collections.unmodifiableList(copy));
        }
        return true;
    }

    @Nullable
    private UploadItem find(@NonNull String id) {
        List<UploadItem> list = current();
        int index = indexOf(list, id);
        return index < 0 ? null : list.get(index);
    }

    private static int indexOf(@NonNull List<UploadItem> list, @NonNull String id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void showMessage(@NonNull String text) {
        message.setValue(UiMessage.of(text));
    }

    // ===== 保存与恢复 =====

    @NonNull
    private Bundle saveState() {
        Bundle b = new Bundle();
        b.putParcelableArrayList(STATE_ITEMS, new ArrayList<>(current()));
        b.putBoolean(STATE_INITIALIZED, initialized);
        return b;
    }

    @Nullable
    private List<UploadItem> restore(@Nullable Object saved) {
        if (!(saved instanceof Bundle)) {
            return null;
        }
        Bundle b = (Bundle) saved;
        b.setClassLoader(UploadItem.class.getClassLoader());
        initialized = b.getBoolean(STATE_INITIALIZED);
        return BundleCompat.getParcelableArrayList(b, STATE_ITEMS, UploadItem.class);
    }

    private static final class PendingRemoval {
        final UploadItem item;
        final int index;
        final Disposable timer;

        PendingRemoval(UploadItem item, int index, Disposable timer) {
            this.item = item;
            this.index = index;
            this.timer = timer;
        }
    }
}
