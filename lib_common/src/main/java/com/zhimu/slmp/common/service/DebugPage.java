package com.zhimu.slmp.common.service;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 单独运行时调试启动页列出的页面：标题、RouterPath 中的 action 与参数。
 */
public final class DebugPage {

    @NonNull
    private final String title;
    @NonNull
    private final String action;
    @Nullable
    private final Bundle extras;

    public DebugPage(@NonNull String title, @NonNull String action) {
        this(title, action, null);
    }

    public DebugPage(@NonNull String title, @NonNull String action, @Nullable Bundle extras) {
        this.title = title;
        this.action = action;
        this.extras = extras;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getAction() {
        return action;
    }

    @Nullable
    public Bundle getExtras() {
        return extras;
    }
}
