package com.zhimu.server.common.api;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分页结果：list、total、page、size、has_more，需要分段计数时带 tab_counts（键原样输出，不做大小写转换）。
 * 可被继承以在 list 同级追加字段（例如 MessagePageVO 的 filtered_unread）。
 */
@Getter
@Setter
@NoArgsConstructor
public class PageVO<T> {

    private List<T> list = new ArrayList<>();
    private long total;
    private int page;
    private int size;
    private boolean hasMore;
    private Map<String, Integer> tabCounts;

    public PageVO(List<T> list, long total, int page, int size) {
        this.list = list == null ? new ArrayList<>() : list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.hasMore = (long) page * size < total;
    }

    public static <T> PageVO<T> of(List<T> list, long total, int page, int size) {
        return new PageVO<>(list, total, page, size);
    }

    public static <T> PageVO<T> empty(int page, int size) {
        return new PageVO<>(new ArrayList<>(), 0, page, size);
    }

    public PageVO<T> withTabCounts(Map<String, Integer> tabCounts) {
        this.tabCounts = tabCounts;
        return this;
    }
}
