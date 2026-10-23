package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** API-D28 消毒计划列表项（D31–D34 返回）。 */
@Data
public class DisinfectPlanVO {

    private Long id;
    private Integer version;
    private List<Long> penIds = new ArrayList<>();
    private String penNamesText;
    private Integer cycleDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private String dueTime;
    private String disinfectantName;
    private String concentration;
    private String methodCode;
    private String methodName;
    /** ACTIVE / PAUSED / ENDED */
    private String status;
    private String statusText;
    private LocalDate nextTaskDate;
    private String nextTaskText;
    private String pauseText;
    private List<String> actions = new ArrayList<>();
    /** 仅新建返回 */
    private String toast;
    /** 仅新建返回：生成的任务数 */
    private Integer taskCount;

    /** 由实体回填时使用 */
    public LocalTime dueTimeValue() {
        return dueTime == null ? null : LocalTime.parse(dueTime.length() > 5 ? dueTime.substring(0, 5) : dueTime);
    }
}
