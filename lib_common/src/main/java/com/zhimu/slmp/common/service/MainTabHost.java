package com.zhimu.slmp.common.service;

import androidx.annotation.NonNull;
import android.os.Bundle;
import androidx.annotation.Nullable;

/**
 * 主页标签切换（MainActivity 实现）：一级页内跳到其他标签，例如今日首页“去栏舍与存栏”。
 * 用法：{@code if (requireActivity() instanceof MainTabHost) ((MainTabHost) requireActivity()).selectTab(TabRegistry.MINE);}
 */
public interface MainTabHost {

    /** 切换到指定标签；当前角色没有该标签时忽略 */
    void selectTab(@NonNull String tabKey);

    /**
     * 切换标签并把参数交给该标签的 Fragment（例如今日首页切到“我的栏舍”并定位到 pen_id）。
     * Fragment 在 onViewCreated 中用 {@code getParentFragmentManager().setFragmentResultListener(
     * MainTabHost.argsResultKey(tabKey), getViewLifecycleOwner(), ...)} 接收。
     */
    default void selectTab(@NonNull String tabKey, @Nullable Bundle args) {
        selectTab(tabKey);
    }

    /** 标签参数的 FragmentResult 键 */
    @NonNull
    static String argsResultKey(@NonNull String tabKey) {
        return "main_tab_args_" + tabKey;
    }
}
