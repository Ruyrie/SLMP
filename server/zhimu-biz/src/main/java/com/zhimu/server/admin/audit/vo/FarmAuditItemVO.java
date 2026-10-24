package com.zhimu.server.admin.audit.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** ADM-R02 注册审核列表行。 */
@Data
public class FarmAuditItemVO {

    private Long auditId;
    private Long farmId;
    private String farmName;
    private String ownerName;
    /** 查询时为完整号码，Service 输出前打码 */
    private String ownerPhone;
    private String categoryName;
    private Integer scale;
    private String countUnitText;
    private String regionText;
    private Integer imageCount;
    private Integer submitNo;
    private LocalDateTime submittedAt;
    private Integer waitMinutes;
    private String waitText;
    private Boolean overdue;
    private String status;
    private String statusText;
    private String auditorName;
    private LocalDateTime auditedAt;
    private String auditOpinion;
    private String rejectReason;
}
