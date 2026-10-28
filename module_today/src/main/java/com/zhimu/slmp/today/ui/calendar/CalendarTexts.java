package com.zhimu.slmp.today.ui.calendar;

import android.content.Context;

import androidx.annotation.NonNull;

import com.zhimu.slmp.today.R;

/**
 * 日历列表文案：分组标题右侧只写待处理数量（与首页“今日待办”口径一致）。
 */
final class CalendarTexts {

    private CalendarTexts() {
    }

    /** “待处理 N 项”；N 为 0 时“没有待处理” */
    @NonNull
    static String pendingCount(@NonNull Context context, int pending) {
        return pending > 0 ? context.getString(R.string.today_calendar_pending_count, pending)
                : context.getString(R.string.today_calendar_pending_none);
    }
}
