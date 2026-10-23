package com.zhimu.slmp.finance.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.finance.model.FinanceConst;

import java.util.Objects;

/**
 * 经营报表查询（API-E37–E42 共用，E41 作为请求体）：不可变。metric 只在 E39 使用，其余请求置 null。
 */
public final class ReportQuery {

    /** WEEKLY / MONTHLY / TO_DATE */
    @NonNull
    private final String reportType;
    /** 周报、月报必填；TO_DATE 不传 */
    @Nullable
    private final String periodStart;
    /** 空或 ALL */
    @Nullable
    private final String scope;
    /** FARM（默认）/ CATEGORY / BATCH */
    @Nullable
    private final String rangeType;
    @Nullable
    private final Long rangeRefId;
    @Nullable
    private final String metric;

    public ReportQuery(@NonNull String reportType, @Nullable String periodStart, @Nullable String scope,
                       @Nullable String rangeType, @Nullable Long rangeRefId, @Nullable String metric) {
        this.reportType = reportType;
        this.periodStart = periodStart;
        this.scope = scope;
        this.rangeType = rangeType;
        this.rangeRefId = rangeRefId;
        this.metric = metric;
    }

    @NonNull
    public static ReportQuery toDate() {
        return new ReportQuery(FinanceConst.REPORT_TO_DATE, null, null, FinanceConst.RANGE_FARM, null, null);
    }

    @NonNull
    public ReportQuery withType(@NonNull String type, @Nullable String period) {
        return new ReportQuery(type, FinanceConst.REPORT_TO_DATE.equals(type) ? null : period, scope, rangeType,
                rangeRefId, null);
    }

    @NonNull
    public ReportQuery withPeriod(@Nullable String period) {
        return new ReportQuery(reportType, period, scope, rangeType, rangeRefId, null);
    }

    @NonNull
    public ReportQuery withScope(@Nullable String s) {
        // 合计视角下“按批次”的批次属于原养殖场，切换时范围回到整场
        return new ReportQuery(reportType, periodStart, s, FinanceConst.RANGE_FARM, null, null);
    }

    @NonNull
    public ReportQuery withRange(@NonNull String type, @Nullable Long refId) {
        return new ReportQuery(reportType, periodStart, scope, type,
                FinanceConst.RANGE_FARM.equals(type) ? null : refId, null);
    }

    @NonNull
    public ReportQuery withMetric(@Nullable String m) {
        return new ReportQuery(reportType, periodStart, scope, rangeType, rangeRefId, m);
    }

    @NonNull
    public ReportExportRequest toExport(@NonNull String format) {
        return new ReportExportRequest(reportType, periodStart, scope, rangeType, rangeRefId, format);
    }

    @NonNull
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

    @Nullable
    public String getMetric() {
        return metric;
    }

    public boolean isAllFarms() {
        return FinanceConst.SCOPE_ALL.equals(scope);
    }

    /** 周报、月报还没有选中期次时不能请求 E38 */
    public boolean isComplete() {
        return FinanceConst.REPORT_TO_DATE.equals(reportType) || periodStart != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReportQuery)) {
            return false;
        }
        ReportQuery q = (ReportQuery) o;
        return reportType.equals(q.reportType) && Objects.equals(periodStart, q.periodStart)
                && Objects.equals(scope, q.scope) && Objects.equals(rangeType, q.rangeType)
                && Objects.equals(rangeRefId, q.rangeRefId) && Objects.equals(metric, q.metric);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reportType, periodStart, scope, rangeType, rangeRefId, metric);
    }
}
