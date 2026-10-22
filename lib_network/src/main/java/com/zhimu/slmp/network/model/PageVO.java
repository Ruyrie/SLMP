package com.zhimu.slmp.network.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.mvvm.EmptyAware;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 分页结果：list、total、page（从 1 开始）、size、has_more；需要分段计数的列表另带 tab_counts。
 */
public class PageVO<T> implements EmptyAware {

    @Nullable
    private List<T> list;
    private int total;
    private int page;
    private int size;
    private boolean hasMore;
    @Nullable
    private Map<String, Integer> tabCounts;

    public PageVO() {
    }

    public PageVO(@Nullable List<T> list, int total, int page, int size, boolean hasMore,
                  @Nullable Map<String, Integer> tabCounts) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.hasMore = hasMore;
        this.tabCounts = tabCounts;
    }

    @NonNull
    public List<T> getList() {
        return list == null ? Collections.emptyList() : list;
    }

    public int getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    /** 各筛选项数量，没有时为空 Map */
    @NonNull
    public Map<String, Integer> getTabCounts() {
        return tabCounts == null ? Collections.emptyMap() : tabCounts;
    }

    @Override
    public boolean isEmptyContent() {
        return getList().isEmpty();
    }
}
