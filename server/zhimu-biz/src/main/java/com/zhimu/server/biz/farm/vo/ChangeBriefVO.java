package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** 资料变更摘要（FarmDetailVO.pending_change / rejected_change） */
@Data
public class ChangeBriefVO {
    private Long changeId;
    private String submittedAt;
    private String rejectReason;
    private String auditedAt;
}
