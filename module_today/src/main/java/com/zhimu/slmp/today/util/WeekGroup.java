package com.zhimu.slmp.today.util;

import androidx.annotation.NonNull;

import com.zhimu.slmp.today.model.vo.TodoItemVO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 周视图的一天，分两段：
 * <ul>
 * <li>“待处理”：标题“10月19日 周一 · 今天 / 待处理 N 项”、当前显示的未完成待办；剩余数 = total 减已显示条数，
 * 点“展开其余 N 项”按 status=UNFINISHED 取全部；</li>
 * <li>“已完成 / 已忽略 · M 项”：默认收起，点开后按 status=CLOSED 加载；M 为 0 时不显示。</li>
 * </ul>
 */
public final class WeekGroup {

    @NonNull
    private final LocalDate date;
    @NonNull
    private final String title;
    /** 未完成条数（待处理 N） */
    private final int total;
    @NonNull
    private final List<TodoItemVO> items;
    /** 已展开（显示当天全部未完成） */
    private final boolean expanded;
    /** 正在加载其余未完成项 */
    private final boolean expanding;
    /** 已完成 + 已忽略条数（M） */
    private final int closedTotal;
    @NonNull
    private final List<TodoItemVO> closedItems;
    /** “已完成 / 已忽略”已展开 */
    private final boolean closedExpanded;
    /** 正在加载已关闭项 */
    private final boolean closedLoading;

    public WeekGroup(@NonNull LocalDate date, @NonNull String title, int total,
                     @NonNull List<TodoItemVO> items, boolean expanded, boolean expanding) {
        this(date, title, total, items, expanded, expanding, 0, Collections.emptyList(), false, false);
    }

    public WeekGroup(@NonNull LocalDate date, @NonNull String title, int total,
                     @NonNull List<TodoItemVO> items, boolean expanded, boolean expanding,
                     int closedTotal, @NonNull List<TodoItemVO> closedItems, boolean closedExpanded,
                     boolean closedLoading) {
        this.date = date;
        this.title = title;
        this.total = total;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.expanded = expanded;
        this.expanding = expanding;
        this.closedTotal = closedTotal;
        this.closedItems = Collections.unmodifiableList(new ArrayList<>(closedItems));
        this.closedExpanded = closedExpanded;
        this.closedLoading = closedLoading;
    }

    @NonNull
    public LocalDate getDate() {
        return date;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    /** 未完成条数 */
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

    /** 收起时剩余的未完成条数 */
    public int getRemaining() {
        return Math.max(0, total - items.size());
    }

    public int getClosedTotal() {
        return closedTotal;
    }

    @NonNull
    public List<TodoItemVO> getClosedItems() {
        return closedItems;
    }

    public boolean isClosedExpanded() {
        return closedExpanded;
    }

    public boolean isClosedLoading() {
        return closedLoading;
    }

    @NonNull
    public WeekGroup withItems(@NonNull List<TodoItemVO> newItems, boolean newExpanded) {
        return new WeekGroup(date, title, total, newItems, newExpanded, false,
                closedTotal, closedItems, closedExpanded, closedLoading);
    }

    @NonNull
    public WeekGroup withExpanding(boolean value) {
        return new WeekGroup(date, title, total, items, expanded, value,
                closedTotal, closedItems, closedExpanded, closedLoading);
    }

    /** 已关闭段：展开并显示 newItems，或收起（清空） */
    @NonNull
    public WeekGroup withClosed(@NonNull List<TodoItemVO> newItems, boolean newExpanded) {
        return new WeekGroup(date, title, total, items, expanded, expanding,
                closedTotal, newExpanded ? newItems : Collections.emptyList(), newExpanded, false);
    }

    @NonNull
    public WeekGroup withClosedLoading(boolean value) {
        return new WeekGroup(date, title, total, items, expanded, expanding,
                closedTotal, closedItems, closedExpanded, value);
    }
}
