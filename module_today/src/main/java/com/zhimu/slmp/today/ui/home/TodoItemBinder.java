package com.zhimu.slmp.today.ui.home;

import android.graphics.Paint;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.today.databinding.TodayItemTodoBinding;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.util.TodayBindingAdapters;
import com.zhimu.slmp.today.util.TodoTypeStyle;
import com.zhimu.slmp.today.util.Texts;

/**
 * 待办列表项绑定（T01、T03 共用）：图标与色调、主文字（已完成加删除线，已完成 / 已忽略为 on-surface-variant）、
 * 养殖场标签（showFarmTag）+ 副文字、尾部状态标签、分组圆角。
 */
public final class TodoItemBinder {

    private TodoItemBinder() {
    }

    public static void bind(@NonNull TodayItemTodoBinding b, @NonNull TodoItemVO item, boolean showFarmTag,
                            int index, int size, boolean pageMargin) {
        b.icon.setImageResource(TodoTypeStyle.iconOf(item.getTodoType()));
        TodayBindingAdapters.applyTone(b.icon, TodoTypeStyle.toneOf(item));

        b.title.setText(item.getTitle());
        boolean closed = TodoTypeStyle.isClosed(item);
        b.title.setTextColor(MaterialColors.getColor(b.title, closed
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : com.google.android.material.R.attr.colorOnSurface));
        int flags = b.title.getPaintFlags();
        boolean strike = TodoTypeStyle.STATUS_DONE.equals(item.getDisplayStatus());
        b.title.setPaintFlags(strike ? flags | Paint.STRIKE_THRU_TEXT_FLAG : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);

        boolean farm = showFarmTag && !Texts.isEmpty(item.getFarmName());
        b.farmTag.setVisibility(farm ? android.view.View.VISIBLE : android.view.View.GONE);
        if (farm) {
            b.farmTag.setText(item.getFarmName());
        }
        String sub = TodoTypeStyle.subTextOf(item);
        b.subtitle.setText(sub);
        b.subtitle.setVisibility(Texts.isEmpty(sub) && !farm ? android.view.View.GONE : android.view.View.VISIBLE);

        if (Texts.isEmpty(item.getTailText())) {
            b.tag.setVisibility(android.view.View.GONE);
        } else {
            b.tag.setVisibility(android.view.View.VISIBLE);
            b.tag.setStatus(item.getTailText(), TodoTypeStyle.tagTypeOf(item));
            b.tag.setTagIcon(TodoTypeStyle.tagIconOf(item));
        }
        TodayBindingAdapters.applyGroup(b.card, index, size, pageMargin);
        b.card.setContentDescription(Texts.join("，", java.util.Arrays.asList(
                item.getTitle(), farm ? item.getFarmName() : null, sub, item.getTailText())));
    }
}
