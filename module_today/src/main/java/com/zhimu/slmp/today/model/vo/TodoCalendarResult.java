package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * 日期汇总（API-T03；客户端暂定类名）：范围内每天一项。
 */
public class TodoCalendarResult {

    @Nullable
    private List<CalendarDayVO> days;

    @Nullable
    public List<CalendarDayVO> getDays() {
        return days;
    }
}
