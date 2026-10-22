package com.zhimu.slmp.base.ui;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * edge-to-edge 下的系统栏 insets 处理：把状态栏、导航栏、刘海（可选软键盘）的尺寸加到视图内边距上，保留布局原有内边距。
 */
public final class InsetsHelper {

    private InsetsHelper() {
    }

    /**
     * @param top    是否处理顶部（状态栏）
     * @param bottom 是否处理底部（导航栏与软键盘，取二者较大值）
     */
    public static void applySystemBarsPadding(@NonNull View view, boolean top, boolean bottom) {
        final int pl = view.getPaddingLeft();
        final int pt = view.getPaddingTop();
        final int pr = view.getPaddingRight();
        final int pb = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(pl + bars.left,
                    pt + (top ? bars.top : 0),
                    pr + bars.right,
                    pb + (bottom ? Math.max(bars.bottom, ime.bottom) : 0));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** 只处理底部（例如底部导航、底部固定操作栏） */
    public static void applyBottomPadding(@NonNull View view) {
        final int pb = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    pb + Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** 只处理顶部（例如延伸到状态栏下的顶部栏） */
    public static void applyTopPadding(@NonNull View view) {
        final int pt = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), pt + bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
