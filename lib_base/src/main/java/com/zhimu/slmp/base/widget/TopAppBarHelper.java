package com.zhimu.slmp.base.widget;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuItemCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;

/**
 * 顶部栏辅助方法（02 号《UI设计规范》2.5、7.1 节），配合 {@code base_include_top_app_bar} 使用。
 * <ul>
 *     <li>{@link #setupBack}：二级页返回按钮执行 OnBackPressedDispatcher（与返回键、预测性返回手势一致）</li>
 *     <li>{@link #tintMenu}：右侧操作图标着色为 on-surface-variant（图标资源默认黑色）</li>
 *     <li>{@link #bindLiftOnScroll}：内容向上滚动后顶部栏底色由 surface 变为 surface-container，不加阴影</li>
 * </ul>
 */
public final class TopAppBarHelper {

    private TopAppBarHelper() {
    }

    /** 设置标题并让返回图标执行返回 */
    public static void setupBack(@NonNull MaterialToolbar toolbar, @Nullable CharSequence title) {
        if (title != null) {
            toolbar.setTitle(title);
        }
        toolbar.setNavigationOnClickListener(v -> {
            Activity activity = findActivity(v.getContext());
            if (activity instanceof ComponentActivity) {
                ((ComponentActivity) activity).getOnBackPressedDispatcher().onBackPressed();
            } else if (activity != null) {
                activity.finish();
            }
        });
    }

    /** 加载菜单并把图标着色为 on-surface-variant；二级页右侧最多 2 个图标，其余收进 more_vert */
    public static void inflateMenu(@NonNull MaterialToolbar toolbar, int menuRes) {
        toolbar.getMenu().clear();
        toolbar.inflateMenu(menuRes);
        tintMenu(toolbar);
    }

    /** 把菜单图标着色为 on-surface-variant */
    public static void tintMenu(@NonNull MaterialToolbar toolbar) {
        int color = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurfaceVariant);
        Menu menu = toolbar.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            Drawable icon = item.getIcon();
            if (icon != null) {
                MenuItemCompat.setIconTintList(item, ColorStateList.valueOf(color));
            }
        }
    }

    /**
     * 不在 CoordinatorLayout 中时手动绑定滚动变色：scrollingView 可向上滚动时顶部栏为 surface-container。
     * 在 CoordinatorLayout + appbar_scrolling_view_behavior 中时 AppBarLayout 的 liftOnScroll 已自动处理，无需调用。
     */
    public static void bindLiftOnScroll(@NonNull AppBarLayout appBar, @NonNull View scrollingView) {
        appBar.setLiftOnScroll(true);
        appBar.setLiftableOverrideEnabled(true);
        appBar.setLiftable(true);
        appBar.setLifted(scrollingView.canScrollVertically(-1));
        scrollingView.setOnScrollChangeListener((v, x, y, oldX, oldY) ->
                appBar.setLifted(v.canScrollVertically(-1)));
    }

    @Nullable
    static Activity findActivity(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
