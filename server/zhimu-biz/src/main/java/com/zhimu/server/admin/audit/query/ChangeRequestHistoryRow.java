package com.zhimu.server.admin.audit.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 同一原记录已处理的修改申请（ADM-R11 record_history）。 */
@Getter
@Setter
public class ChangeRequestHistoryRow {

    private Long id;
    private String fieldName;
    private String beforeDisplay;
    private String afterDisplay;
    private String reviewerName;
    private LocalDateTime reviewedAt;
    private String status;
    private String recalcSummary;
}
