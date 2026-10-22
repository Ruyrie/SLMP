package com.zhimu.slmp.base.dialog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.zhimu.slmp.base.R;

import java.lang.ref.WeakReference;

/**
 * 第 2 级弹窗：提示气泡（02 号《UI设计规范》7.11、8.1 节），解释名词和指标（料肉比、休药期、死亡率口径），
 * 由旁边的 {@code info} 图标触发。在触发图标的上方（空间不足时下方）就地弹出，不遮挡被解释的数值；
 * 点其他位置或按返回键关闭；不加遮罩，气泡里不放输入框。同一时刻只显示一个气泡。
 * <ul>
 *     <li>普通气泡 {@link #show(View, CharSequence)}：一句话，inverse-surface 底，body-s 12 sp，圆角 4 dp</li>
 *     <li>富气泡 {@link #showRich}：标题 title-s + 说明 body-m + 最多一个文字按钮，surface-container 底，圆角 12 dp，
 *     elevation-2，最宽 312 dp</li>
 * </ul>
 * <pre>
 * binding.infoFcr.setOnClickListener(v -&gt;
 *         TipPopup.showRich(v, getString(R.string.herd_tip_fcr_title), getString(R.string.herd_tip_fcr), null, null));
 * </pre>
 * 文案来自字符串资源。页面销毁时自动关闭。
 */
public final class TipPopup {

    @Nullable
    private static WeakReference<PopupWindow> current;

    private TipPopup() {
    }

    /** 普通气泡 */
    @NonNull
    public static PopupWindow show(@NonNull View anchor, @NonNull CharSequence text) {
        Context context = anchor.getContext();
        TextView content = (TextView) LayoutInflater.from(context).inflate(R.layout.base_popup_tip, null, false);
        content.setText(text);
        content.setMaxWidth(maxWidth(context));
        float radius = context.getResources().getDimension(R.dimen.base_radius_xs);
        int color = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorSurfaceInverse);
        return showPopup(anchor, content, background(radius, color), 0f);
    }

    public static PopupWindow show(@NonNull View anchor, @StringRes int text) {
        return show(anchor, anchor.getContext().getText(text));
    }

    /**
     * 富气泡。
     *
     * @param actionText 文字按钮（可为空）；点击后执行 action 并关闭气泡
     */
    @NonNull
    public static PopupWindow showRich(@NonNull View anchor, @Nullable CharSequence title, @NonNull CharSequence message,
                                       @Nullable CharSequence actionText, @Nullable View.OnClickListener action) {
        Context context = anchor.getContext();
        View content = LayoutInflater.from(context).inflate(R.layout.base_popup_tip_rich, null, false);
        TextView titleView = content.findViewById(R.id.base_tip_title);
        TextView messageView = content.findViewById(R.id.base_tip_message);
        MaterialButton actionView = content.findViewById(R.id.base_tip_action);
        int max = Math.min(maxWidth(context), context.getResources().getDimensionPixelSize(R.dimen.base_tip_rich_max_width));
        int innerMax = max - content.getPaddingLeft() - content.getPaddingRight();
        titleView.setMaxWidth(innerMax);
        messageView.setMaxWidth(innerMax);
        titleView.setText(title);
        titleView.setVisibility(TextUtils.isEmpty(title) ? View.GONE : View.VISIBLE);
        messageView.setText(message);
        if (TextUtils.isEmpty(title)) {
            ((ViewGroup.MarginLayoutParams) messageView.getLayoutParams()).topMargin = 0;
        }
        final PopupWindow[] holder = new PopupWindow[1];
        if (!TextUtils.isEmpty(actionText)) {
            actionView.setText(actionText);
            actionView.setVisibility(View.VISIBLE);
            actionView.setOnClickListener(v -> {
                if (holder[0] != null) {
                    holder[0].dismiss();
                }
                if (action != null) {
                    action.onClick(anchor);
                }
            });
        }
        float radius = context.getResources().getDimension(R.dimen.base_radius_md);
        int color = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorSurfaceContainer);
        PopupWindow popup = showPopup(anchor, content, background(radius, color),
                context.getResources().getDimension(R.dimen.base_elevation_2));
        holder[0] = popup;
        return popup;
    }

    /** 关闭当前气泡 */
    public static void dismiss() {
        PopupWindow popup = current == null ? null : current.get();
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
        }
        current = null;
    }

    @NonNull
    private static PopupWindow showPopup(@NonNull View anchor, @NonNull View content,
                                         @NonNull MaterialShapeDrawable background, float elevation) {
        dismiss();
        Context context = anchor.getContext();
        PopupWindow popup = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(background);
        popup.setElevation(elevation);
        popup.setOutsideTouchable(true);
        popup.setTouchable(true);

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int margin = context.getResources().getDimensionPixelSize(R.dimen.base_page_margin);
        int gap = context.getResources().getDimensionPixelSize(R.dimen.base_space_1);
        content.measure(View.MeasureSpec.makeMeasureSpec(screenWidth - margin * 2, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int w = content.getMeasuredWidth();
        int h = content.getMeasuredHeight();

        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        Rect visible = new Rect();
        anchor.getWindowVisibleDisplayFrame(visible);
        int x = loc[0] + anchor.getWidth() / 2 - w / 2;
        x = Math.max(margin, Math.min(x, screenWidth - margin - w));
        int above = loc[1] - gap - h;
        int y = above >= visible.top ? above : loc[1] + anchor.getHeight() + gap;

        popup.showAtLocation(anchor.getRootView(), Gravity.TOP | Gravity.START, x, y);
        current = new WeakReference<>(popup);

        if (context instanceof LifecycleOwner) {
            Lifecycle lifecycle = ((LifecycleOwner) context).getLifecycle();
            DefaultLifecycleObserver observer = new DefaultLifecycleObserver() {
                @Override
                public void onDestroy(@NonNull LifecycleOwner owner) {
                    if (popup.isShowing()) {
                        popup.dismiss();
                    }
                }
            };
            lifecycle.addObserver(observer);
            popup.setOnDismissListener(() -> lifecycle.removeObserver(observer));
        }
        return popup;
    }

    @NonNull
    private static MaterialShapeDrawable background(float radius, int color) {
        MaterialShapeDrawable bg = new MaterialShapeDrawable(ShapeAppearanceModel.builder()
                .setAllCornerSizes(radius)
                .build());
        bg.setFillColor(ColorStateList.valueOf(color));
        return bg;
    }

    private static int maxWidth(@NonNull Context context) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int margin = context.getResources().getDimensionPixelSize(R.dimen.base_page_margin);
        return screenWidth - margin * 2;
    }
}
