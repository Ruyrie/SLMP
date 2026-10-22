package com.zhimu.slmp.common.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 底部导航标签注册表：业务组件在 IModuleEntry.onCreate 中注册一级页 Fragment，MainActivity 按角色装配。
 * 所有者与场长：今日、生产、助手、经营、我的；员工：今日、我的栏舍、助手、我的。
 */
public final class TabRegistry {

    public static final String TODAY = "today";
    public static final String PRODUCTION = "production";
    public static final String MY_PENS = "my_pens";
    public static final String ASSISTANT = "assistant";
    public static final String BUSINESS = "business";
    public static final String MINE = "mine";

    private static final ConcurrentHashMap<String, Supplier<Fragment>> TABS = new ConcurrentHashMap<>();

    private TabRegistry() {
    }

    public static void register(@NonNull String tabKey, @NonNull Supplier<Fragment> factory) {
        TABS.put(tabKey, factory);
    }

    /** 创建标签页 Fragment；没有注册时返回 null（MainActivity 显示占位页） */
    @Nullable
    public static Fragment create(@NonNull String tabKey) {
        Supplier<Fragment> s = TABS.get(tabKey);
        return s == null ? null : s.get();
    }

    public static boolean isRegistered(@NonNull String tabKey) {
        return TABS.containsKey(tabKey);
    }
}
