package com.zhimu.slmp.finance.util;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 页面跳转目标：ViewModel 只发出 action 与参数（Map 便于单元测试），页面转换为 Bundle 后经 Router 打开。
 * action 为 RouterPath 常量；forResult 为真时页面用已注册的 launcher 打开（返回后刷新）。
 */
public final class NavTarget {

    @NonNull
    private final String action;
    private final Map<String, Object> extras = new LinkedHashMap<>();
    private boolean forResult;
    private boolean finishSelf;

    private NavTarget(@NonNull String action) {
        this.action = action;
    }

    @NonNull
    public static NavTarget of(@NonNull String action) {
        return new NavTarget(action);
    }

    @NonNull
    public NavTarget put(@NonNull String key, @Nullable Object value) {
        if (value != null) {
            extras.put(key, value);
        }
        return this;
    }

    /** 以 Activity Result 打开 */
    @NonNull
    public NavTarget forResult() {
        forResult = true;
        return this;
    }

    /** 打开后关闭当前页（E06 新增成功后以 E07 替换当前页） */
    @NonNull
    public NavTarget replace() {
        finishSelf = true;
        return this;
    }

    @NonNull
    public String getAction() {
        return action;
    }

    @NonNull
    public Map<String, Object> getExtras() {
        return extras;
    }

    @Nullable
    public Object get(@NonNull String key) {
        return extras.get(key);
    }

    public boolean isForResult() {
        return forResult;
    }

    public boolean isFinishSelf() {
        return finishSelf;
    }

    @NonNull
    public Bundle toBundle() {
        Bundle b = new Bundle();
        for (Map.Entry<String, Object> e : extras.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Long) {
                b.putLong(e.getKey(), (Long) v);
            } else if (v instanceof Integer) {
                b.putInt(e.getKey(), (Integer) v);
            } else if (v instanceof Boolean) {
                b.putBoolean(e.getKey(), (Boolean) v);
            } else {
                b.putString(e.getKey(), String.valueOf(v));
            }
        }
        return b;
    }
}
