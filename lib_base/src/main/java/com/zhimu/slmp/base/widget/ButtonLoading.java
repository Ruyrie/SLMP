package com.zhimu.slmp.base.widget;

import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;
import com.zhimu.slmp.base.R;

/**
 * 按钮内的加载状态（02 号《UI设计规范》7.5、8.2 节）：提交时文字或图标位置换成 20 dp 环形进度（颜色同文字色），
 * 按钮保持原宽度并不再响应点击；结束后恢复原文字与图标。不弹全屏加载对话框。
 * <pre>
 * ButtonLoading.setLoading(binding.btnSubmit, true);   // 提交中
 * ButtonLoading.setLoading(binding.btnSubmit, false);  // 成功或失败后恢复
 * </pre>
 * 在 DataBinding 中可用 {@code app:loading="@{vm.submitting}"}（见 {@link WidgetBindingAdapters}）。
 */
public final class ButtonLoading {

    private ButtonLoading() {
    }

    public static boolean isLoading(@NonNull MaterialButton button) {
        return Boolean.TRUE.equals(button.getTag(R.id.base_tag_loading));
    }

    public static void setLoading(@NonNull MaterialButton button, boolean loading) {
        if (loading == isLoading(button)) {
            return;
        }
        if (loading) {
            button.setTag(R.id.base_tag_loading, Boolean.TRUE);
            button.setTag(R.id.base_tag_loading_text, button.getText());
            button.setTag(R.id.base_tag_loading_icon, button.getIcon());
            button.setTag(R.id.base_tag_loading_icon_padding, button.getIconPadding());
            button.setTag(R.id.base_tag_loading_min_width, button.getMinWidth());
            button.setTag(R.id.base_tag_loading_clickable, button.isClickable());
            if (button.getWidth() > 0) {
                button.setMinWidth(button.getWidth());
            }
            int size = button.getResources().getDimensionPixelSize(R.dimen.base_button_progress_size);
            CircularProgressIndicatorSpec spec = new CircularProgressIndicatorSpec(button.getContext(), null, 0,
                    com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall);
            spec.indicatorSize = size;
            spec.indicatorInset = 0;
            spec.trackThickness = Math.max(1, Math.round(size / 10f));
            spec.indicatorTrackGapSize = 0;
            spec.indicatorColors = new int[]{button.getCurrentTextColor()};
            Drawable progress = IndeterminateDrawable.createCircularDrawable(button.getContext(), spec);
            button.setTag(R.id.base_tag_loading_progress, progress);
            ensureDetachListener(button);
            button.setText("");
            button.setIconPadding(0);
            button.setIcon(progress);
            button.setClickable(false);
            button.setContentDescription(button.getResources().getString(R.string.base_cd_loading));
        } else {
            Object text = button.getTag(R.id.base_tag_loading_text);
            Object icon = button.getTag(R.id.base_tag_loading_icon);
            Object padding = button.getTag(R.id.base_tag_loading_icon_padding);
            Object minWidth = button.getTag(R.id.base_tag_loading_min_width);
            Object clickable = button.getTag(R.id.base_tag_loading_clickable);
            stopProgress(button);
            button.setIcon(icon instanceof Drawable ? (Drawable) icon : null);
            button.setIconPadding(padding instanceof Integer ? (Integer) padding : 0);
            button.setText(text instanceof CharSequence ? (CharSequence) text : "");
            button.setMinWidth(minWidth instanceof Integer ? (Integer) minWidth : 0);
            button.setClickable(!(clickable instanceof Boolean) || (Boolean) clickable);
            button.setContentDescription(null);
            button.setTag(R.id.base_tag_loading, null);
            button.setTag(R.id.base_tag_loading_text, null);
            button.setTag(R.id.base_tag_loading_icon, null);
            button.setTag(R.id.base_tag_loading_progress, null);
        }
    }

    /** 停止环形进度动画：动画由全局 AnimationHandler 持有，不停止会让已关闭的页面无法回收 */
    private static void stopProgress(@NonNull MaterialButton button) {
        Object progress = button.getTag(R.id.base_tag_loading_progress);
        if (progress instanceof Drawable) {
            ((Drawable) progress).setVisible(false, false);
        }
    }

    /** 按钮离开窗口（页面关闭）时停止动画，重新进入窗口且仍在加载时恢复 */
    private static void ensureDetachListener(@NonNull MaterialButton button) {
        if (button.getTag(R.id.base_tag_loading_listener) != null) {
            return;
        }
        View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                Object progress = v.getTag(R.id.base_tag_loading_progress);
                if (progress instanceof Drawable && isLoading((MaterialButton) v)) {
                    ((Drawable) progress).setVisible(true, false);
                }
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                stopProgress((MaterialButton) v);
            }
        };
        button.addOnAttachStateChangeListener(listener);
        button.setTag(R.id.base_tag_loading_listener, listener);
    }
}
