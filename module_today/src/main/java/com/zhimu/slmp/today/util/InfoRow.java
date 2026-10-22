package com.zhimu.slmp.today.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 待办详情信息分组中的一行：标签、值、值下方的辅助说明（可空）。值为空的行不生成。
 */
public final class InfoRow {

    @NonNull
    private final String label;
    @NonNull
    private final String value;
    @Nullable
    private final String hint;

    public InfoRow(@NonNull String label, @NonNull String value, @Nullable String hint) {
        this.label = label;
        this.value = value;
        this.hint = hint;
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    @NonNull
    public String getValue() {
        return value;
    }

    @Nullable
    public String getHint() {
        return hint;
    }
}
