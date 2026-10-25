package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** API-D36 消毒任务列表项。 */
@Data
public class DisinfectTaskVO {

    private Long id;
    private Long planId;
    private Long penId;
    private String penName;
    private LocalDate taskDate;
    private String dateText;
    private String dueTime;
    private String disinfectantName;
    private String concentration;
    private String methodName;
    private Integer cycleDays;
    private String executorText;
    private Boolean noStaff;
    /** PLAN / MANUAL 等任务来源 */
    private String source;
    private String sourceText;
    /** PENDING / OVERDUE / DONE / PAUSED / CANCELLED；已过截止时刻的 PENDING 返回 OVERDUE */
    private String status;
    private String statusText;
    private LocalDateTime actualTime;
    private String actualExecutorName;
    private Long recordId;
    private Boolean manualDone;
    private List<String> actions = new ArrayList<>();
}
