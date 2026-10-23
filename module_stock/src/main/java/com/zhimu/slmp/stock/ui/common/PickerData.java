package com.zhimu.slmp.stock.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 选择面板的数据：列表、error 色说明行（命中禁用药）、空状态文字、底部说明。 */
public final class PickerData {

    @NonNull
    public final List<PickerItem> items;
    @Nullable
    public final String errorLine;
    @Nullable
    public final String emptyText;
    @Nullable
    public final String footnote;

    public PickerData(@Nullable List<PickerItem> items, @Nullable String errorLine, @Nullable String emptyText,
                      @Nullable String footnote) {
        this.items = items == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(items));
        this.errorLine = errorLine;
        this.emptyText = emptyText;
        this.footnote = footnote;
    }

    @NonNull
    public static PickerData of(@Nullable List<PickerItem> items, @Nullable String emptyText) {
        return new PickerData(items, null, emptyText, null);
    }

    /** 可点选的项数（不含分组标题） */
    public int itemCount() {
        int n = 0;
        for (PickerItem i : items) {
            if (i.type == PickerItem.TYPE_ITEM) {
                n++;
            }
        }
        return n;
    }

    @Nullable
    public PickerItem find(@NonNull String value) {
        for (PickerItem i : items) {
            if (i.type == PickerItem.TYPE_ITEM && i.value.equals(value)) {
                return i;
            }
        }
        return null;
    }
}
