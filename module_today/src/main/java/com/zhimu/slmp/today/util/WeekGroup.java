package com.zhimu.slmp.today.util;

import androidx.annotation.NonNull;

import com.zhimu.slmp.today.model.vo.TodoItemVO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 周视图的一天：标题“10月19日 周一 · 今天”、当天总数、当前显示的待办；剩余数 = total 减已显示条数。
 */
public final class WeekGroup {

    @NonNull
    private final LocalDate date;
    @NonNull
    private final String title;
    private final int total;
    @NonNull
    private final List<TodoItemVO> items;
    /** 已展开（显示当天全部） */
    private final boolean expanded;
    /** 正在加载其余项 */
    private final boolean expanding;

    public WeekGroup(@NonNull LocalDate date, @NonNull String title, int total, @NonNull List<TodoItemVO> items,
                     boolean expanded, boolean expanding) {
        this.date = date;
        this.title = title;
        this.total = total;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.expanded = expanded;
        this.expanding = expanding;
    }

    @NonNull
    public LocalDate getDate() {
        return date;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    public int getTotal() {
        return total;
    }

    @NonNull
    public List<TodoItemVO> getItems() {
        return items;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public boolean isExpanding() {
        return expanding;
    }

    /** 收起时剩余的条数 */
    public int getRemaining() {
        return Math.max(0, total - items.size());
    }

    @NonNull
    public WeekGroup withItems(@NonNull List<TodoItemVO> newItems, boolean newExpanded) {
        return new WeekGroup(date, title, total, newItems, newExpanded, false);
    }

    @NonNull
    public WeekGroup withExpanding(boolean value) {
        return new WeekGroup(date, title, total, items, expanded, value);
    }
}
