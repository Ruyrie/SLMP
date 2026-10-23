package com.zhimu.server.biz.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 报表 AI 摘要（t_biz_report_summary）；ALL 视角 farm_id 为空，不标 @FarmScoped。 */
@Getter
@Setter
@TableName("t_biz_report_summary")
public class BizReportSummary extends AuditEntity {

    private String viewKey;
    private String scopeType;
    private Long farmId;
    private Long userId;
    private String reportType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String rangeType;
    private Long rangeRefId;
    private String highlight;
    private String problem;
    private String suggestion;
    private String dataHash;
    private String status;
    private String errorMessage;
    private String aiModel;
    private String promptVersion;
    private LocalDateTime startedAt;
    private LocalDateTime generatedAt;
}
