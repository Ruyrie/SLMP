package com.zhimu.slmp.base.ui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;
import com.zhimu.slmp.base.mvvm.UiMessage;

import java.lang.ref.WeakReference;

/**
 * 第 1 级弹窗 Snackbar：4 秒自动消失，最多一个操作；锚定在底部导航或底部操作栏上方；新的替换旧的；不使用 Toast。
 * 样式由主题的 snackbarStyle 等属性提供。
 * <p>
 * 锚点：调用方传入，或在布局中给底部导航 / 底部操作栏设置 {@code android:tag="snackbar_anchor"}。
 */
public final class SnackbarHelper {

    /** 布局中作为 Snackbar 锚点的视图标签 */
    public static final String ANCHOR_TAG = "snackbar_anchor";

    /** Snackbar 操作点击回调 */
    public interface OnActionListener {
        void onAction(@NonNull String actionId);
    }

    @Nullable
    private static WeakReference<Snackbar> current;

    private SnackbarHelper() {
    }

    /** 在页面上显示，锚点自动查找带 ANCHOR_TAG 的可见视图 */
    @Nullable
    public static Snackbar show(@NonNull Activity activity, @NonNull UiMessage message) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return null;
        }
        return show(content, message, findAnchor(content), null);
    }

    @Nullable
    public static Snackbar show(@NonNull Activity activity, @NonNull String text) {
        return show(activity, UiMessage.of(text));
    }

    /**
     * 显示 Snackbar。
     *
     * @param host     任一位于页面中的视图
     * @param anchor   锚点（底部导航或底部操作栏），为 null 时显示在底部
     * @param listener 操作回调，message 无操作时可为 null
     */
    @NonNull
    public static Snackbar show(@NonNull View host, @NonNull UiMessage message, @Nullable View anchor,
                                @Nullable OnActionListener listener) {
        dismissCurrent();
        Snackbar bar = Snackbar.make(host, message.getText(), Snackbar.LENGTH_LONG);
        bar.setDuration(message.getDurationMs());
        if (message.hasAction()) {
            final String actionId = message.getActionId();
            bar.setAction(message.getActionText(), v -> {
                if (listener != null && actionId != null) {
                    listener.onAction(actionId);
                }
            });
        }
        if (anchor != null && anchor.isShown()) {
            bar.setAnchorView(anchor);
        }
        bar.show();
        current = new WeakReference<>(bar);
        return bar;
    }

    /** 关闭当前显示的 Snackbar */
    public static void dismissCurrent() {
        Snackbar bar = current == null ? null : current.get();
        if (bar != null && bar.isShownOrQueued()) {
            bar.dismiss();
        }
        current = null;
    }

    /** 在视图树中查找带 ANCHOR_TAG 的可见视图 */
    @Nullable
    public static View findAnchor(@Nullable View root) {
        if (root == null) {
            return null;
        }
        View v = root instanceof ViewGroup ? root.findViewWithTag(ANCHOR_TAG) : null;
        return v != null && v.isShown() ? v : null;
    }
}
