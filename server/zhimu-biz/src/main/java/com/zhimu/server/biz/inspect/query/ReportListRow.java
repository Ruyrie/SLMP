package com.zhimu.server.biz.inspect.query;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 异常上报列表行（联表栏舍/批次/上报人后由 Service 转 VO）。 */
@Data
public class ReportListRow {

    private Long reportId;
    private Integer version;
    private LocalDateTime reportedAt;
    private LocalDate reportDate;
    private Long penId;
    private String penName;
    private Long batchId;
    private String batchNo;
    private Integer sickCount;
    private Integer stockSnapshot;
    private BigDecimal sickRate;
    private String description;
    private String status;
    private String aiState;
    private String urgency;
    private Boolean isMajorEpidemic;
    private String topCauseName;
    private Long reporterId;
    private String reporterName;
    private Long inspectionId;
    private String source;
    private String symptomNames;
}
