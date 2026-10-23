package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 员工版首页指标（API-T01 staff_metrics，仅员工）。 */
@Getter
@Setter
public class StaffMetricsVO {

    private Integer penCount;
    private Integer todayTodoCount;
    private Integer overdueCount;
    private Integer todayDoneCount;
    private List<String> penNames;
    private List<String> todayDoneNames;
    private String overdueFirstText;
}
