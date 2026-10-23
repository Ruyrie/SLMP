package com.zhimu.server.biz.finance.service;

import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.vo.FinanceRecordVos;
import com.zhimu.server.biz.finance.vo.OverviewVos;

/** 收支流水与经营概览（API-E01–E07、E43）。 */
public interface FinanceRecordService {

    OverviewVos.BusinessOverviewVO overview();

    OverviewVos.FinanceSummaryVO monthlySummary(String month, String scope);

    FinanceRecordVos.RecordPageVO listRecords(FinanceQueries.FinanceRecordQuery query);

    FinanceRecordVos.DetailVO detail(Long id);

    FinanceRecordVos.RecordVO create(FinanceRequests.FinanceRecordRequest request, String idempotencyKey);

    FinanceRecordVos.RecordVO update(Long id, FinanceRequests.FinanceRecordRequest request);

    FinanceRecordVos.RecordVO voidRecord(Long id, Integer version);

    FinanceRecordVos.SubmissionVO submission(String clientRequestId);
}
