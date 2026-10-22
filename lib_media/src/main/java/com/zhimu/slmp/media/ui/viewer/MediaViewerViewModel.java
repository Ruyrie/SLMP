package com.zhimu.slmp.media.ui.viewer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.media.data.MediaRepository;
import com.zhimu.slmp.media.util.GallerySaver;
import com.zhimu.slmp.media.util.MediaGlide;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

/**
 * I09 全屏预览（模块07 文档 2.4.10、2.7.4）：图片缩放、视频播放（ExoPlayer 由页面持有，进入时不自动播放，切页暂停）、
 * 静音与倍速、保存到相册（图片 Pictures/智牧通、视频 Movies/智牧通）、从表单进入时“删除”（4 秒内可撤销，关闭时回传）。
 * 参数：{@link MediaViewer#EXTRA_ITEMS}、{@link MediaViewer#EXTRA_START_INDEX}、{@link MediaViewer#EXTRA_DELETABLE}。
 */
public class MediaViewerViewModel extends BaseViewModel {

    public static final String MSG_SAVED = "已保存到相册";
    public static final String MSG_SAVE_FAILED = "保存失败，请检查存储空间";
    /** 保留给旧调用方的常量（视频已支持播放） */
    public static final String MSG_VIDEO_UNSUPPORTED = "视频播放将在后续版本支持";
    public static final String MSG_DELETED_PHOTO = "已删除 1 个照片";
    public static final String MSG_DELETED_VIDEO = "已删除 1 段视频";
    public static final String ACTION_UNDO_DELETE = "media_viewer_undo_delete";

    /** 倍速选项 */
    public static final float[] SPEEDS = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    private static final String STATE_INDEX = "media_viewer_index";
    private static final String STATE_DELETED = "media_viewer_deleted";

    private final MediaRepository repository;
    private final Context context;

    private final MutableLiveData<List<ViewerItem>> items = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> currentIndex = new MutableLiveData<>(0);
    private final MutableLiveData<String> indexText = new MutableLiveData<>("");
    private final MutableLiveData<ViewerItem> currentItem = new MutableLiveData<>();
    private final MutableLiveData<Boolean> chromeVisible = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> deletable = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> muted = new MutableLiveData<>(false);
    private final MutableLiveData<Float> speed = new MutableLiveData<>(1.0f);
    private final SingleLiveEvent<Void> close = new SingleLiveEvent<>();
    /** 已调用过 API-U06 的 file_id（每个文件只刷新一次地址） */
    private final Set<Long> refreshed = new HashSet<>();
    /** 删除记录（撤销用）：项与原位置 */
    private final Deque<Object[]> undoStack = new ArrayDeque<>();
    private final ArrayList<String> deletedKeys = new ArrayList<>();

    @Inject
    public MediaViewerViewModel(@NonNull MediaRepository repository, @NonNull Context context) {
        this.repository = repository;
        this.context = context.getApplicationContext();
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        ArrayList<String> savedDeleted = handle.get(STATE_DELETED);
        if (savedDeleted != null) {
            deletedKeys.addAll(savedDeleted);
        }
        List<ViewerItem> list = new ArrayList<>();
        Object raw = handle.get(MediaViewer.EXTRA_ITEMS);
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof ViewerItem && !deletedKeys.contains(((ViewerItem) o).getKey())) {
                    list.add((ViewerItem) o);
                }
            }
        }
        deletable.setValue(Boolean.TRUE.equals(handle.get(MediaViewer.EXTRA_DELETABLE)));
        Integer saved = handle.get(STATE_INDEX);
        Integer start = handle.get(MediaViewer.EXTRA_START_INDEX);
        int index = saved != null ? saved : start != null ? start : 0;
        items.setValue(Collections.unmodifiableList(list));
        currentIndex.setValue(list.isEmpty() ? 0 : Math.max(0, Math.min(index, list.size() - 1)));
        updateDerived();
        if (list.isEmpty()) {
            close.call();
        }
    }

    // ===== 对外 =====

    public LiveData<List<ViewerItem>> getItems() {
        return items;
    }

    public LiveData<Integer> getCurrentIndex() {
        return currentIndex;
    }

    /** “3 / 6” */
    public LiveData<String> getIndexText() {
        return indexText;
    }

    /** 当前项（来源说明两行） */
    public LiveData<ViewerItem> getCurrentItem() {
        return currentItem;
    }

    public LiveData<Boolean> getChromeVisible() {
        return chromeVisible;
    }

    public LiveData<Boolean> getSaving() {
        return saving;
    }

    /** 从表单进入：显示更多菜单“删除” */
    public LiveData<Boolean> getDeletable() {
        return deletable;
    }

    public LiveData<Boolean> getMuted() {
        return muted;
    }

    public LiveData<Float> getSpeed() {
        return speed;
    }

    public SingleLiveEvent<Void> getClose() {
        return close;
    }

    /** 被删除项的 key（关闭时回传给网格） */
    @NonNull
    public ArrayList<String> getDeletedKeys() {
        return new ArrayList<>(deletedKeys);
    }

    // ===== 操作 =====

    public void selectPage(int position) {
        Integer cur = currentIndex.getValue();
        if (cur == null || cur != position) {
            currentIndex.setValue(position);
            getSavedState().set(STATE_INDEX, position);
            updateDerived();
        }
    }

    public void toggleChrome() {
        chromeVisible.setValue(!Boolean.TRUE.equals(chromeVisible.getValue()));
    }

    public void onClose() {
        close.call();
    }

    public void toggleMute() {
        muted.setValue(!Boolean.TRUE.equals(muted.getValue()));
    }

    public void setSpeed(float value) {
        speed.setValue(value);
    }

    /** “1.25x” */
    @NonNull
    public static String speedLabel(float value) {
        String s = String.format(Locale.ROOT, "%.2f", value);
        while (s.endsWith("0") && s.indexOf('.') < s.length() - 2) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "x";
    }

    /** 保存当前照片或视频到相册（MediaStore，不申请存储权限） */
    public void save() {
        ViewerItem item = currentItem.getValue();
        if (item == null || Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        String url = item.getUrl();
        if (url == null) {
            showMessage(MSG_SAVE_FAILED);
            return;
        }
        saving.setValue(true);
        launch(item.isVideo() ? GallerySaver.saveVideo(context, url) : GallerySaver.saveImage(context, url,
                item.getFileId()), () -> {
            saving.setValue(false);
            showMessage(MSG_SAVED);
        }, e -> {
            saving.setValue(false);
            showMessage(MSG_SAVE_FAILED);
        });
    }

    /** 从表单进入时删除当前项：自动切到下一项，删完最后一项时关闭；Snackbar 提供“撤销” */
    public void deleteCurrent() {
        List<ViewerItem> list = items.getValue();
        Integer index = currentIndex.getValue();
        if (!Boolean.TRUE.equals(deletable.getValue()) || list == null || list.isEmpty() || index == null) {
            return;
        }
        int i = Math.max(0, Math.min(index, list.size() - 1));
        List<ViewerItem> copy = new ArrayList<>(list);
        ViewerItem removed = copy.remove(i);
        undoStack.push(new Object[]{removed, i});
        if (removed.getKey() != null) {
            deletedKeys.add(removed.getKey());
            getSavedState().set(STATE_DELETED, new ArrayList<>(deletedKeys));
        }
        items.setValue(Collections.unmodifiableList(copy));
        if (copy.isEmpty()) {
            close.call();
            return;
        }
        int next = Math.min(i, copy.size() - 1);
        currentIndex.setValue(next);
        getSavedState().set(STATE_INDEX, next);
        updateDerived();
        showMessage(UiMessage.withAction(removed.isVideo() ? MSG_DELETED_VIDEO : MSG_DELETED_PHOTO, "撤销",
                ACTION_UNDO_DELETE));
    }

    /** 撤销最近一次删除 */
    public void undoDelete() {
        Object[] last = undoStack.poll();
        List<ViewerItem> list = items.getValue();
        if (last == null || list == null) {
            return;
        }
        ViewerItem item = (ViewerItem) last[0];
        int pos = Math.min((Integer) last[1], list.size());
        List<ViewerItem> copy = new ArrayList<>(list);
        copy.add(pos, item);
        if (item.getKey() != null) {
            deletedKeys.remove(item.getKey());
            getSavedState().set(STATE_DELETED, new ArrayList<>(deletedKeys));
        }
        items.setValue(Collections.unmodifiableList(copy));
        currentIndex.setValue(pos);
        getSavedState().set(STATE_INDEX, pos);
        updateDerived();
    }

    /** 加载失败：服务端文件调用 API-U06 取新的临时地址后重新加载（每个文件一次）；返回是否发起了刷新 */
    public boolean refreshUrl(int position) {
        List<ViewerItem> list = items.getValue();
        if (list == null || position < 0 || position >= list.size()) {
            return false;
        }
        ViewerItem item = list.get(position);
        long fileId = item.getFileId();
        if (fileId <= 0 || !MediaGlide.isRemoteUrl(item.getUrl()) || !refreshed.add(fileId)) {
            return false;
        }
        launch(repository.getFile(fileId), vo -> {
            List<ViewerItem> cur = items.getValue();
            if (cur == null || vo.getUrl() == null) {
                return;
            }
            List<ViewerItem> copy = new ArrayList<>(cur);
            for (int i = 0; i < copy.size(); i++) {
                if (copy.get(i).getFileId() == fileId) {
                    copy.set(i, copy.get(i).withUrl(vo.getUrl()));
                }
            }
            items.setValue(Collections.unmodifiableList(copy));
            updateDerived();
        }, e -> {
            // 换地址失败时保持“加载失败”的占位，不再提示
        });
        return true;
    }

    private void updateDerived() {
        List<ViewerItem> list = items.getValue();
        Integer index = currentIndex.getValue();
        if (list == null || list.isEmpty() || index == null) {
            indexText.setValue("");
            currentItem.setValue(null);
            return;
        }
        int i = Math.max(0, Math.min(index, list.size() - 1));
        indexText.setValue(String.format(Locale.ROOT, "%d / %d", i + 1, list.size()));
        currentItem.setValue(list.get(i));
    }

    @Nullable
    ViewerItem itemAt(int position) {
        List<ViewerItem> list = items.getValue();
        return list == null || position < 0 || position >= list.size() ? null : list.get(position);
    }
}
