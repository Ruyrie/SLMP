package com.zhimu.slmp.breed.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 登记成功后关闭页面的结果：提示文案与新生成的自繁批次（有批次时 Snackbar 带“查看批次”）。
 */
public final class FinishEvent {

    @NonNull
    private final String text;
    @Nullable
    private final Long batchId;

    public FinishEvent(@NonNull String text, @Nullable Long batchId) {
        this.text = text;
        this.batchId = batchId;
    }

    @NonNull
    public String getText() {
        return text;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }
}
