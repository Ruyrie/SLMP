package com.zhimu.slmp.common.service;

import androidx.annotation.NonNull;

/**
 * 主页标签切换（MainActivity 实现）：一级页内跳到其他标签，例如今日首页“去栏舍与存栏”。
 * 用法：{@code if (requireActivity() instanceof MainTabHost) ((MainTabHost) requireActivity()).selectTab(TabRegistry.MINE);}
 */
public interface MainTabHost {

    /** 切换到指定标签；当前角色没有该标签时忽略 */
    void selectTab(@NonNull String tabKey);
}
