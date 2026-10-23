package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 周视图中的一天（API-T05）：在 CalendarDayVO 基础上带前 per_day 项。 */
@Getter
@Setter
public class WeeklyDayVO extends CalendarDayVO {

    private List<TodoItemVO> items;
}
