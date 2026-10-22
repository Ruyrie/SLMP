package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-D26 删除用药记录的 data（toast：已删除用药记录；待定事项 4 外层结构联调核对）。
 */
public class ToastVO {

    @Nullable
    private String toast;

    @Nullable
    public String getToast() {
        return toast;
    }
}
