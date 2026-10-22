package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * 日历中的一天（API-T03）：红点 overdue_count、蓝点 pending_count、灰点 closed_count。
 */
public class CalendarDayVO {

    @Nullable
    private String date;
    private boolean isToday;
    /** 含已完成、已忽略 */
    private int total;
    private int overdueCount;
    private int pendingCount;
    private int closedCount;
    /** 最多 2 个 */
    @Nullable
    private List<String> overdueTitles;

    @Nullable
    public String getDate() {
        return date;
    }

    public boolean isToday() {
        return isToday;
    }

    public int getTotal() {
        return total;
    }

    public int getOverdueCount() {
        return overdueCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public int getClosedCount() {
        return closedCount;
    }

    @Nullable
    public List<String> getOverdueTitles() {
        return overdueTitles;
    }
}
