package com.zhimu.slmp.herd.ui.common;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.router.RouterTable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 跳转目标（ViewModel 的 navEvent）：RouterPath 的 action 与参数。参数先放在 Map 中（便于单元测试），
 * 页面打开时转为 Bundle。文档写作“lib_common 的 NavTarget”，lib_common 尚未提供，先在本组件内定义。
 */
public final class NavTarget {

    @NonNull
    private final String action;
    private final Map<String, Object> extras = new LinkedHashMap<>();
    private boolean finishCurrent;
    private boolean forResult;

    private NavTarget(@NonNull String action) {
        this.action = action;
    }

    @NonNull
    public static NavTarget of(@NonNull String action) {
        return new NavTarget(action);
    }

    /** 按页面编号（route 字段，例如 "D05"）；未知编号返回 null */
    @Nullable
    public static NavTarget ofPage(@Nullable String pageCode) {
        String action = RouterTable.actionOf(pageCode);
        return action == null ? null : new NavTarget(action);
    }

    @NonNull
    public NavTarget put(@NonNull String key, @Nullable Object value) {
        if (value != null) {
            extras.put(key, value);
        }
        return this;
    }

    /** 打开后关闭当前页面 */
    @NonNull
    public NavTarget finishCurrent() {
        this.finishCurrent = true;
        return this;
    }

    /** 需要接收返回结果（返回后刷新当前页面） */
    @NonNull
    public NavTarget forResult() {
        this.forResult = true;
        return this;
    }

    @NonNull
    public String getAction() {
        return action;
    }

    @NonNull
    public Map<String, Object> getExtras() {
        return Collections.unmodifiableMap(extras);
    }

    @Nullable
    public Object get(@NonNull String key) {
        return extras.get(key);
    }

    public boolean isFinishCurrent() {
        return finishCurrent;
    }

    public boolean isForResult() {
        return forResult;
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
