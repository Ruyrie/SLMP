package com.zhimu.slmp.stock.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/** 分页列表的一行：分组标题（“10月19日 周一 · 今天”“需要处理 · 3 个批号”）或数据项。 */
public final class ListRow<T> {

    @Nullable
    public final String header;
    @Nullable
    public final T item;
    /** 行的稳定键（DiffUtil） */
    @NonNull
    public final String key;

    private ListRow(@Nullable String header, @Nullable T item, @NonNull String key) {
        this.header = header;
        this.item = item;
        this.key = key;
    }

    @NonNull
    public static <T> ListRow<T> header(@NonNull String text, @NonNull String key) {
        return new ListRow<>(text, null, "h:" + key);
    }

    @NonNull
    public static <T> ListRow<T> item(@NonNull T item, @NonNull String key) {
        return new ListRow<>(null, item, "i:" + key);
    }

    public boolean isHeader() {
        return header != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ListRow)) {
            return false;
        }
        ListRow<?> r = (ListRow<?>) o;
        return key.equals(r.key) && Objects.equals(header, r.header) && item == r.item;
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
