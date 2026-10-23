package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 周视图分组（API-T05）：没有待办的日期不返回。 */
@Getter
@Setter
public class TodoWeeklyResult {

    private List<WeeklyDayVO> days;
}
