package com.zhimu.slmp.stock.ui.common;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;

/** 选择面板的一项（或分组标题）；由宿主 ViewModel 按 VO 组装。 */
public final class PickerItem {

    public static final int TYPE_ITEM = 0;
    public static final int TYPE_HEADER = 1;

    public final int type;
    @NonNull
    public final String value;
    @NonNull
    public final String title;
    @Nullable
    public String subtitle;
    @Nullable
    public String trailing;
    @Nullable
    public String tag;
    @StatusTagView.TagType
    public int tagType = StatusTagView.NEUTRAL;
    @DrawableRes
    public int tagIcon;
    @DrawableRes
    public int icon;
    @ListRowView.Tone
    public int tone = ListRowView.TONE_SECONDARY;
    /** 不可选：置灰、点按无反应 */
    public boolean disabled;
    public boolean selected;
    /** 行内文字按钮（“去报废”） */
    @Nullable
    public String actionText;

    private PickerItem(int type, @NonNull String value, @NonNull String title) {
        this.type = type;
        this.value = value;
        this.title = title;
    }

    @NonNull
    public static PickerItem header(@NonNull String title) {
        return new PickerItem(TYPE_HEADER, "", title);
    }

    @NonNull
    public static PickerItem item(@NonNull String value, @NonNull String title) {
        return new PickerItem(TYPE_ITEM, value, title);
    }

    @NonNull
    public PickerItem subtitle(@Nullable String s) {
        subtitle = s;
        return this;
    }

    @NonNull
    public PickerItem trailing(@Nullable String s) {
        trailing = s;
        return this;
    }

    @NonNull
    public PickerItem tag(@Nullable String text, @StatusTagView.TagType int type) {
        tag = text;
        tagType = type;
        return this;
    }

    @NonNull
    public PickerItem tagIcon(@DrawableRes int res) {
        tagIcon = res;
        return this;
    }

    @NonNull
    public PickerItem icon(@DrawableRes int res, @ListRowView.Tone int t) {
        icon = res;
        tone = t;
        return this;
    }

    @NonNull
    public PickerItem disabled(boolean d) {
        disabled = d;
        return this;
    }

    @NonNull
    public PickerItem selected(boolean s) {
        selected = s;
        return this;
    }

    @NonNull
    public PickerItem action(@Nullable String text) {
        actionText = text;
        return this;
    }
}
