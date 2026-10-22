package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * 周视图中的一天（API-T05）：在 CalendarDayVO 基础上带前 per_day 项。
 */
public class WeeklyDayVO extends CalendarDayVO {

    @Nullable
    private List<TodoItemVO> items;

    @Nullable
    public List<TodoItemVO> getItems() {
        return items;
    }
}
