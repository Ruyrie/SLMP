package com.zhimu.slmp.finance.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.ReportExportRequest;
import com.zhimu.slmp.finance.model.request.ReportQuery;
import com.zhimu.slmp.finance.model.vo.BusinessReportVO;
import com.zhimu.slmp.finance.model.vo.ReportExportVO;
import com.zhimu.slmp.finance.model.vo.ReportPeriodVO;
import com.zhimu.slmp.finance.model.vo.ReportSummaryVO;
import com.zhimu.slmp.finance.model.vo.ReportTrendVO;
import com.zhimu.slmp.network.RxResult;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

/**
 * 经营报表仓库：API-E37–E42（E42 用长超时的 ReportExportApi）。
 */
@FeatureScope
public class ReportRepository {

    private final FinanceApi api;
    private final ReportExportApi exportApi;

    @Inject
    public ReportRepository(@NonNull FinanceApi api, @NonNull ReportExportApi exportApi) {
        this.api = api;
        this.exportApi = exportApi;
    }

    /** API-E37（只取周报、月报） */
    @NonNull
    public Single<ReportPeriodVO> getPeriods(@NonNull String reportType, @Nullable String scope) {
        return api.getPeriods(reportType, scope).compose(RxResult.unwrap());
    }

    /** API-E38 */
    @NonNull
    public Single<BusinessReportVO> getReport(@NonNull ReportQuery q) {
        return api.getReport(q.getReportType(), q.getPeriodStart(), q.getScope(), q.getRangeType(),
                q.getRangeRefId()).compose(RxResult.unwrap());
    }

    /** API-E39：q.metric 必填 */
    @NonNull
    public Single<ReportTrendVO> getReportTrend(@NonNull ReportQuery q) {
        String metric = q.getMetric() == null ? FinanceConst.METRIC_STOCK : q.getMetric();
        return api.getReportTrend(q.getReportType(), q.getPeriodStart(), q.getScope(), q.getRangeType(),
                q.getRangeRefId(), metric).compose(RxResult.unwrap());
    }

    /** API-E40 */
    @NonNull
    public Single<ReportSummaryVO> getSummary(@NonNull ReportQuery q) {
        return api.getSummary(q.getReportType(), q.getPeriodStart(), q.getScope(), q.getRangeType(),
                q.getRangeRefId()).compose(RxResult.unwrap());
    }

    /**
     * API-E40 轮询：0 秒起每 2 秒，状态不为 GENERATING 且不是旧内容（outdated）时结束，150 秒上限；
     * outdated 为真时先显示旧内容并继续轮询。
     */
    @NonNull
    public Observable<ReportSummaryVO> pollSummary(@NonNull ReportQuery q) {
        long times = MarketRepository.POLL_MAX_SEC / MarketRepository.POLL_INTERVAL_SEC + 1;
        return Observable.interval(0, MarketRepository.POLL_INTERVAL_SEC, TimeUnit.SECONDS)
                .take(times)
                .concatMapSingle(t -> getSummary(q))
                .takeUntil(v -> !FinanceConst.GEN_GENERATING.equals(v.getStatus()) && !v.isOutdated());
    }

    /** API-E41 */
    @NonNull
    public Single<ReportSummaryVO> regenerateSummary(@NonNull ReportQuery q) {
        return api.regenerateSummary(q.withMetric(null)).compose(RxResult.unwrap());
    }

    /** API-E42 */
    @NonNull
    public Single<ReportExportVO> exportReport(@NonNull ReportExportRequest r) {
        return exportApi.export(r).compose(RxResult.unwrap());
    }
}
