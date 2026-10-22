package com.zhimu.slmp.base.widget;

import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;
import androidx.databinding.BindingMethod;
import androidx.databinding.BindingMethods;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.zhimu.slmp.base.mvvm.UiState;

/**
 * UI 套件控件的 DataBinding 适配：
 * <ul>
 *     <li>{@code app:uiState="@{vm.uiState}"}：StateLayout 渲染页面状态</li>
 *     <li>{@code app:loading="@{vm.submitting}"}：MaterialButton 按钮内加载</li>
 *     <li>{@code app:rowTitle / rowSubtitle / rowTrailingText}、{@code app:kvValue / kvNote}、
 *     {@code app:statValue / statLabel / statUnit}、{@code app:noticeText}、{@code app:sectionTitle}、
 *     {@code app:avatarName}、{@code app:barPrimaryText}、{@code app:barPrimaryEnabled}、{@code app:barLoading}
 *     可直接绑定表达式</li>
 * </ul>
 */
@BindingMethods({
        @BindingMethod(type = ListRowView.class, attribute = "rowTitle", method = "setTitle"),
        @BindingMethod(type = ListRowView.class, attribute = "rowSubtitle", method = "setSubtitle"),
        @BindingMethod(type = ListRowView.class, attribute = "rowTrailingText", method = "setTrailingText"),
        @BindingMethod(type = ListRowView.class, attribute = "rowUnread", method = "setUnread"),
        @BindingMethod(type = ListRowView.class, attribute = "rowShowChevron", method = "setShowChevron"),
        @BindingMethod(type = ListRowView.class, attribute = "groupPosition", method = "setGroupPosition"),
        @BindingMethod(type = KeyValueRowView.class, attribute = "kvKey", method = "setKey"),
        @BindingMethod(type = KeyValueRowView.class, attribute = "kvValue", method = "setValue"),
        @BindingMethod(type = KeyValueRowView.class, attribute = "kvNote", method = "setNote"),
        @BindingMethod(type = KeyValueRowView.class, attribute = "groupPosition", method = "setGroupPosition"),
        @BindingMethod(type = StatCardView.class, attribute = "statLabel", method = "setLabel"),
        @BindingMethod(type = StatCardView.class, attribute = "statValue", method = "setValue"),
        @BindingMethod(type = StatCardView.class, attribute = "statUnit", method = "setUnit"),
        @BindingMethod(type = StatCardView.class, attribute = "statEmphasis", method = "setEmphasis"),
        @BindingMethod(type = NoticeView.class, attribute = "noticeText", method = "setText"),
        @BindingMethod(type = NoticeView.class, attribute = "noticeLevel", method = "setLevel"),
        @BindingMethod(type = SectionHeaderView.class, attribute = "sectionTitle", method = "setTitle"),
        @BindingMethod(type = AvatarView.class, attribute = "avatarName", method = "setName"),
        @BindingMethod(type = StatusTagView.class, attribute = "tagType", method = "setTagType"),
        @BindingMethod(type = BottomActionBar.class, attribute = "barPrimaryText", method = "setPrimaryText"),
        @BindingMethod(type = BottomActionBar.class, attribute = "barPrimaryEnabled", method = "setPrimaryEnabled"),
        @BindingMethod(type = BottomActionBar.class, attribute = "barLoading", method = "setPrimaryLoading"),
})
public final class WidgetBindingAdapters {

    private WidgetBindingAdapters() {
    }

    /**
     * 顶部栏标题：只在绑定值非空时设置，避免 include 未传 title 时把代码中
     * TopAppBarHelper.setupBack 设置的标题清空。
     */
    @BindingAdapter("topBarTitle")
    public static void setTopBarTitle(MaterialToolbar toolbar, @Nullable CharSequence title) {
        if (title != null) {
            toolbar.setTitle(title);
        }
    }

    @BindingAdapter("uiState")
    public static void setUiState(StateLayout layout, @Nullable UiState state) {
        layout.render(state);
    }

    @BindingAdapter("loading")
    public static void setLoading(MaterialButton button, @Nullable Boolean loading) {
        ButtonLoading.setLoading(button, Boolean.TRUE.equals(loading));
    }

    @BindingAdapter(value = {"noticeTitle", "noticeText"}, requireAll = true)
    public static void setNotice(NoticeView view, @Nullable CharSequence title, @Nullable CharSequence text) {
        view.setNotice(title, text);
    }
}
