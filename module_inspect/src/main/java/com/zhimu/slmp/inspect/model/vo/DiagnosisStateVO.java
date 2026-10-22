package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.Nullable;

/**
 * API-I20 / I21 诊断状态（I06 轮询、重新诊断）。
 */
public class DiagnosisStateVO {

    private long reportId;
    private String status;
    private String aiState;
    private String diagnosedAt;
    private String urgency;
    private String lastFailureText;
    private boolean autoRetryPending;

    public long getReportId() { return reportId; }

    public String getStatus() { return status; }

    public String getAiState() { return aiState; }

    @Nullable
    public String getDiagnosedAt() { return diagnosedAt; }

    @Nullable
    public String getUrgency() { return urgency; }

    @Nullable
    public String getLastFailureText() { return lastFailureText; }

    public boolean isAutoRetryPending() { return autoRetryPending; }

    public boolean isDiagnosing() { return DiagnosisDetailVO.AI_DIAGNOSING.equals(aiState); }
}
