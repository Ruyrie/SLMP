package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

import java.util.Map;

/**
 * 未读数（API-T13）：total_unread 用于一级页角标，filtered_unread 用于“未读 3”。
 */
public class UnreadCountVO {

    private int totalUnread;
    private int filteredUnread;
    @Nullable
    private Map<String, Integer> byCategory;

    public int getTotalUnread() {
        return totalUnread;
    }

    public int getFilteredUnread() {
        return filteredUnread;
    }

    @Nullable
    public Map<String, Integer> getByCategory() {
        return byCategory;
    }
}
