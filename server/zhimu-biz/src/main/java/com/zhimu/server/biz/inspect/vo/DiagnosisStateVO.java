package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

/** API-I20 / I21 诊断状态。 */
@Data
public class DiagnosisStateVO {

    private long reportId;
    private String status;
    private String aiState;
    private String diagnosedAt;
    private String urgency;
    private String lastFailureText;
    private boolean autoRetryPending;
}
