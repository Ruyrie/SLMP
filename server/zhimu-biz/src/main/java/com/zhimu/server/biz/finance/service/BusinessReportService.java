package com.zhimu.server.biz.finance.service;

import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.vo.ReportVos;

/** 经营报表（API-E37–E42）。 */
public interface BusinessReportService {

    ReportVos.ReportPeriodVO periods(String reportType, String scope);

    ReportVos.BusinessReportVO view(FinanceQueries.ReportQuery query);

    ReportVos.ReportTrendVO trend(FinanceQueries.ReportQuery query);

    ReportVos.ReportSummaryVO summary(FinanceQueries.ReportQuery query);

    ReportVos.ReportSummaryVO regenerate(FinanceRequests.ReportQuery request);

    ReportVos.ReportExportVO export(FinanceRequests.ReportExportRequest request);
}
