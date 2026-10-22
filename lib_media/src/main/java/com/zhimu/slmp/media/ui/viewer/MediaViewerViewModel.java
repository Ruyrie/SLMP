package com.zhimu.slmp.media.ui.viewer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.media.data.MediaRepository;
import com.zhimu.slmp.media.util.GallerySaver;
import com.zhimu.slmp.media.util.MediaGlide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

/**
 * I09 全屏预览（模块07 文档 2.4.10，本期只做图片；视频项显示封面与“视频播放将在后续版本支持”）。
 * 参数：{@link MediaViewer#EXTRA_ITEMS}、{@link MediaViewer#EXTRA_START_INDEX}。
 */
public class MediaViewerViewModel extends BaseViewModel {

    public static final String MSG_SAVED = "已保存到相册";
    public static final String MSG_SAVE_FAILED = "保存失败，请检查存储空间";
    public static final String MSG_VIDEO_UNSUPPORTED = "视频播放将在后续版本支持";

    private static final String STATE_INDEX = "media_viewer_index";

    private final MediaRepository repository;
    private final Context context;

    private final MutableLiveData<List<ViewerItem>> items = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> currentIndex = new MutableLiveData<>(0);
    private final MutableLiveData<String> indexText = new MutableLiveData<>("");
    private final MutableLiveData<ViewerItem> currentItem = new MutableLiveData<>();
    private final MutableLiveData<Boolean> chromeVisible = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final SingleLiveEvent<Void> close = new SingleLiveEvent<>();
    /** 已调用过 API-U06 的 file_id（每个文件只刷新一次地址） */
    private final Set<Long> refreshed = new HashSet<>();

    @Inject
    public MediaViewerViewModel(@NonNull MediaRepository repository, @NonNull Context context) {
        this.repository = repository;
        this.context = context.getApplicationContext();
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        List<ViewerItem> list = new ArrayList<>();
        Object raw = handle.get(MediaViewer.EXTRA_ITEMS);
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof ViewerItem) {
                    list.add((ViewerItem) o);
                }
            }
        }
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

    public SingleLiveEvent<Void> getClose() {
        return close;
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

    /** 保存当前图片到相册（MediaStore，不申请存储权限） */
    public void save() {
        ViewerItem item = currentItem.getValue();
        if (item == null || Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        if (item.isVideo()) {
            showMessage(MSG_VIDEO_UNSUPPORTED);
            return;
        }
        String url = item.getUrl();
        if (url == null) {
            showMessage(MSG_SAVE_FAILED);
            return;
        }
        saving.setValue(true);
        launch(GallerySaver.saveImage(context, url, item.getFileId()), () -> {
            saving.setValue(false);
            showMessage(MSG_SAVED);
        }, e -> {
            saving.setValue(false);
            showMessage(MSG_SAVE_FAILED);
        });
    }

    /** 图片加载失败：服务端文件调用 API-U06 取新的临时地址后重新加载（每个文件一次） */
    public void refreshUrl(int position) {
        List<ViewerItem> list = items.getValue();
        if (list == null || position < 0 || position >= list.size()) {
            return;
        }
        ViewerItem item = list.get(position);
        long fileId = item.getFileId();
        if (fileId <= 0 || !MediaGlide.isRemoteUrl(item.getUrl()) || !refreshed.add(fileId)) {
            return;
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
            // 换地址失败时保持“图片加载失败”的占位，不再提示
        });
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
