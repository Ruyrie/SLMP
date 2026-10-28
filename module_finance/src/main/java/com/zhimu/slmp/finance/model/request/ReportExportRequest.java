package com.zhimu.slmp.finance.model.request;

import androidx.annotation.Nullable;

/**
 * API-E42 导出：ReportQuery 字段 + format
 * <p>Gson 不序列化 null：可空字段置 null 即不提交。
 */
public class ReportExportRequest {

    private final String reportType;
    @Nullable
    private final String periodStart;
    @Nullable
    private final String scope;
    @Nullable
    private final String rangeType;
    @Nullable
    private final Long rangeRefId;
    /** 只支持 XLSX */
    private final String format;

    public ReportExportRequest(String reportType, @Nullable String periodStart, @Nullable String scope, @Nullable String rangeType, @Nullable Long rangeRefId, String format) {
        this.reportType = reportType;
        this.periodStart = periodStart;
        this.scope = scope;
        this.rangeType = rangeType;
        this.rangeRefId = rangeRefId;
        this.format = format;
    }

    public String getReportType() {
        return reportType;
    }

    @Nullable
    public String getPeriodStart() {
        return periodStart;
    }

    @Nullable
    public String getScope() {
        return scope;
    }

    @Nullable
    public String getRangeType() {
        return rangeType;
    }

    @Nullable
    public Long getRangeRefId() {
        return rangeRefId;
    }

    public String getFormat() {
        return format;
    }
}
