package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-I24 可关联的异常上报与诊断（复用模块07）。
 */
public class LinkableReportVO {

    private long reportId;
    @Nullable
    private Long diagnosisId;
    @Nullable
    private String label;
    @Nullable
    private Long penId;
    @Nullable
    private Long batchId;

    public long getReportId() {
        return reportId;
    }

    @Nullable
    public Long getDiagnosisId() {
        return diagnosisId;
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    @Nullable
    public Long getPenId() {
        return penId;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }
}
