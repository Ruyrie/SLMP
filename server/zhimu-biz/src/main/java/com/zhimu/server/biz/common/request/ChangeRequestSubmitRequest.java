package com.zhimu.server.biz.common.request;

import lombok.Data;

/**
 * API-U02 提交修改申请（App lib_common ChangeRequestSubmitRequest）。
 * DELETE 时 field_code / new_value 可空，reason 与 record_version 必填；UPDATE 时 field_code、new_value 必填。
 */
@Data
public class ChangeRequestSubmitRequest {

    private String bizType;
    private Long bizId;
    /** UPDATE / DELETE / VOID */
    private String changeType;
    private String fieldCode;
    private String newValue;
    private String recordVersion;
    private String reason;
    /** 驳回后重新提交时指向原申请 */
    private Long resubmitOfId;
}
