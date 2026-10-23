package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 日历中的一天（API-T03）：红点 overdue_count、蓝点 pending_count、灰点 closed_count。 */
@Getter
@Setter
public class CalendarDayVO {

    private String date;
    private Boolean isToday;
    /** 含已完成、已忽略 */
    private Integer total;
    private Integer overdueCount;
    private Integer pendingCount;
    private Integer closedCount;
    /** 最多 2 个 */
    private List<String> overdueTitles;

    public static CalendarDayVO of(String date, boolean today) {
        CalendarDayVO vo = new CalendarDayVO();
        vo.date = date;
        vo.isToday = today;
        vo.total = 0;
        vo.overdueCount = 0;
        vo.pendingCount = 0;
        vo.closedCount = 0;
        vo.overdueTitles = List.of();
        return vo;
    }
}
