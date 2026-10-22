package com.zhimu.slmp.inspect.model.vo;

/**
 * API-I16 / I26 上报结果：之后 I06 每 poll_interval_sec 秒调 API-I20，最多 poll_max_sec 秒。
 */
public class ReportCreateResultVO {

    private long reportId;
    private String status;
    private String aiState;
    private int pollIntervalSec;
    private int pollMaxSec;

    public long getReportId() { return reportId; }

    public String getStatus() { return status; }

    public String getAiState() { return aiState; }

    public int getPollIntervalSec() { return pollIntervalSec > 0 ? pollIntervalSec : 3; }

    public int getPollMaxSec() { return pollMaxSec > 0 ? pollMaxSec : 90; }
}
