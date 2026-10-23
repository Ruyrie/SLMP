package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** 审核记录（F06）：type 为 SUBMIT、RESUBMIT、REJECT、APPROVE */
@Data
public class AuditRecordVO {
    private String type;
    private String typeText;
    private String time;
    private String statusTag;
    private String desc;
    private Integer submitNo;
}
