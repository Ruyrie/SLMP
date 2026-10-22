package com.zhimu.slmp.media.ui.viewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.FileVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 打开全屏预览 I09（02 号规范 7.16、10.2 节）：从被点的那一项开始，左右滑动切换；视频进入时不自动播放。
 * <pre>
 * MediaViewer.openFiles(context, detail.getPhotos(), position, "死淘登记 · 3 号育肥舍");
 * </pre>
 * 从可编辑的照片网格打开时用 {@link Contract}（{@link #EXTRA_DELETABLE} 为真，更多菜单有“删除”），
 * 关闭时返回被删除项的 key（UploadItem id），由网格移除。
 */
public final class MediaViewer {

    /** ArrayList&lt;ViewerItem&gt; */
    public static final String EXTRA_ITEMS = "media_items";
    /** int，从 0 开始 */
    public static final String EXTRA_START_INDEX = "start_index";
    /** boolean，从表单进入（更多菜单显示“删除”） */
    public static final String EXTRA_DELETABLE = "deletable";
    /** 结果：被删除项的 key（ArrayList&lt;String&gt;） */
    public static final String RESULT_DELETED_KEYS = "media_deleted_keys";

    private MediaViewer() {
    }

    @NonNull
    public static Bundle extras(@NonNull List<ViewerItem> items, int startIndex) {
        Bundle b = new Bundle();
        b.putParcelableArrayList(EXTRA_ITEMS, new ArrayList<>(items));
        b.putInt(EXTRA_START_INDEX, Math.max(0, Math.min(startIndex, items.size() - 1)));
        return b;
    }

    public static boolean open(@NonNull Context context, @NonNull List<ViewerItem> items, int startIndex) {
        if (items.isEmpty()) {
            return false;
        }
        return Router.open(context, RouterPath.I09, extras(items, startIndex));
    }

    /**
     * 预览服务端文件（FileVO 或 MediaItemVO；MediaItemVO 的来源、拍摄人、拍摄时间自动写成两行说明）。
     *
     * @param sourceText FileVO 没有来源字段时的第一行说明，如“养殖场资质图”
     */
    public static boolean openFiles(@NonNull Context context, @NonNull List<? extends FileVO> files, int startIndex,
                                    @Nullable String sourceText) {
        List<ViewerItem> items = new ArrayList<>(files.size());
        for (FileVO f : files) {
            items.add(ViewerItem.from(f, sourceText));
        }
        return open(context, items, startIndex);
    }

    /** 可编辑网格的预览参数 */
    public static final class Input {
        @NonNull
        final List<ViewerItem> items;
        final int startIndex;
        final boolean deletable;

        public Input(@NonNull List<ViewerItem> items, int startIndex, boolean deletable) {
            this.items = items;
            this.startIndex = startIndex;
            this.deletable = deletable;
        }
    }

    /** 打开 I09 并接收被删除项的 key */
    public static final class Contract extends ActivityResultContract<Input, List<String>> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull Input input) {
            Bundle b = extras(input.items, input.startIndex);
            b.putBoolean(EXTRA_DELETABLE, input.deletable);
            return new Intent(RouterPath.I09).setPackage(context.getPackageName()).putExtras(b);
        }

        @NonNull
        @Override
        public List<String> parseResult(int resultCode, @Nullable Intent intent) {
            if (resultCode != Activity.RESULT_OK || intent == null) {
                return Collections.emptyList();
            }
            ArrayList<String> keys = intent.getStringArrayListExtra(RESULT_DELETED_KEYS);
            return keys == null ? Collections.emptyList() : keys;
        }
    }
}
