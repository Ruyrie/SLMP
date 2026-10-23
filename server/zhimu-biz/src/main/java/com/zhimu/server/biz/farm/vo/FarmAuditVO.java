package com.zhimu.server.biz.farm.vo;

import lombok.Data;

import java.util.List;

/** 审核结果（F05、F06、F07）：已通过的养殖场只返回 farm_id、name、status、status_text */
@Data
public class FarmAuditVO {
    private Long farmId;
    private String name;
    private String status;
    private String statusText;
    private String auditStatus;
    private String firstSubmittedAt;
    private String submittedAt;
    private String restrictTip;
    private Integer submitNo;
    private Integer auditVersion;
    private Integer reshootCount;
    private String auditedAt;
    private String rejectReason;
    private RegistrationVO registration;
    private List<AuditRecordVO> records;
}
