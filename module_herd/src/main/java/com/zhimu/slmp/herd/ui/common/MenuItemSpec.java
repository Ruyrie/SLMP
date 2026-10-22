package com.zhimu.slmp.herd.ui.common;

import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;

import java.util.List;

/**
 * 更多菜单项（ViewModel 组装，页面交给 MoreMenuHelper 显示）。
 */
public final class MenuItemSpec {

    public final int id;
    @NonNull
    public final String title;
    @DrawableRes
    public final int icon;
    @Nullable
    public final String description;
    public final boolean danger;
    public final boolean enabled;
    /** 右侧“需审核” */
    public final boolean review;

    public MenuItemSpec(int id, @NonNull String title, @DrawableRes int icon, @Nullable String description,
                        boolean danger, boolean enabled, boolean review) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.description = description;
        this.danger = danger;
        this.enabled = enabled;
        this.review = review;
    }

    @NonNull
    public static MenuItemSpec normal(int id, @NonNull String title, @DrawableRes int icon) {
        return new MenuItemSpec(id, title, icon, null, false, true, false);
    }

    @NonNull
    public static MenuItemSpec review(int id, @NonNull String title, @DrawableRes int icon) {
        return new MenuItemSpec(id, title, icon, null, false, true, true);
    }

    @NonNull
    public static MenuItemSpec danger(int id, @NonNull String title, @DrawableRes int icon, boolean enabled,
                                      @Nullable String description) {
        return new MenuItemSpec(id, title, icon, description, true, enabled, false);
    }

    /** 用 MoreMenuHelper 显示；没有菜单项时不显示 */
    public static void show(@NonNull View anchor, @NonNull List<MenuItemSpec> items,
                            @NonNull MoreMenuHelper.OnItemClickListener listener) {
        MoreMenuHelper.Builder b = MoreMenuHelper.with(anchor);
        String needsReview = anchor.getContext().getString(R.string.base_menu_needs_review);
        for (MenuItemSpec s : items) {
            b.add(new MoreMenuHelper.Item(s.id, s.title, s.icon, s.description, s.review ? needsReview : null,
                    s.danger, s.enabled));
        }
        b.setOnItemClickListener(listener).show();
    }
}
