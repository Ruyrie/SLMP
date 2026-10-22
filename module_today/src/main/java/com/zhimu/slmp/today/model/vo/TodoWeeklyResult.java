package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * 周视图分组（API-T05；客户端暂定类名）：没有待办的日期不返回。
 */
public class TodoWeeklyResult {

    @Nullable
    private List<WeeklyDayVO> days;

    @Nullable
    public List<WeeklyDayVO> getDays() {
        return days;
    }
}
