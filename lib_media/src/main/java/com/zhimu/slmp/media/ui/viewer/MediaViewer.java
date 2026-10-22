package com.zhimu.slmp.media.ui.viewer;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.FileVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.ArrayList;
import java.util.List;

/**
 * 打开全屏预览 I09（02 号规范 7.16、10.2 节）：从被点的那一项开始，左右滑动切换。
 * <pre>
 * MediaViewer.openFiles(context, detail.getPhotos(), position, "死淘登记 · 3 号育肥舍");
 * </pre>
 */
public final class MediaViewer {

    /** ArrayList&lt;ViewerItem&gt; */
    public static final String EXTRA_ITEMS = "media_items";
    /** int，从 0 开始 */
    public static final String EXTRA_START_INDEX = "start_index";

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
}
