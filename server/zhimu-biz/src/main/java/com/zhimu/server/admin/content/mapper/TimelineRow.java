package com.zhimu.server.admin.content.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/** 通知详情操作记录行（t_operation_log）。 */
@Data
public class TimelineRow {
    private String opAction;
    private String operatorName;
    private String targetName;
    private String reason;
    private String extraText;
    private String notifyText;
    private LocalDateTime createdAt;
}
