package com.zhimu.slmp.breed.ui.common;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ViewModel 发出的页面跳转：action 为 RouterPath 常量（或本组件内部页面 action），forResult 为 true 时用页面的
 * launcher 打开，返回后刷新并显示提示。
 */
public final class NavRequest {

    @NonNull
    private final String action;
    @NonNull
    private final Bundle extras;
    private final boolean forResult;

    private NavRequest(@NonNull String action, @Nullable Bundle extras, boolean forResult) {
        this.action = action;
        this.extras = extras == null ? new Bundle() : extras;
        this.forResult = forResult;
    }

    @NonNull
    public static NavRequest of(@NonNull String action) {
        return new NavRequest(action, null, false);
    }

    @NonNull
    public static NavRequest forResult(@NonNull String action) {
        return new NavRequest(action, null, true);
    }

    @NonNull
    public NavRequest put(@NonNull String key, long value) {
        extras.putLong(key, value);
        return this;
    }

    @NonNull
    public NavRequest put(@NonNull String key, @Nullable String value) {
        extras.putString(key, value);
        return this;
    }

    @NonNull
    public String getAction() {
        return action;
    }

    @NonNull
    public Bundle getExtras() {
        return forResult ? BreedNav.forResult(extras) : extras;
    }

    public boolean isForResult() {
        return forResult;
    }
}
