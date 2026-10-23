package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 日期汇总（API-T03）：范围内每天一项。 */
@Getter
@Setter
public class TodoCalendarResult {

    private List<CalendarDayVO> days;
}
