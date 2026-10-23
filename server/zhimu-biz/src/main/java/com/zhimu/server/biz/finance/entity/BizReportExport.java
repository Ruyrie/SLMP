package com.zhimu.server.biz.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 报表导出记录（t_biz_report_export，只追加，无 updated_at）。 */
@Getter
@Setter
@TableName("t_biz_report_export")
public class BizReportExport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String scopeType;
    private Long farmId;
    private Long userId;
    private String reportType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String rangeType;
    private Long rangeRefId;
    private String fileFormat;
    private Long fileId;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
}
