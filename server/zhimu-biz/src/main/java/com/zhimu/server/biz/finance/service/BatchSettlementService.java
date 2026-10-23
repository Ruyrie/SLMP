package com.zhimu.server.biz.finance.service;

import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.vo.SettlementVos;

/** 批次核算（API-E26–E28）。 */
public interface BatchSettlementService {

    SettlementVos.OptionPageVO options(FinanceQueries.SettlementQuery query);

    SettlementVos.BatchSettlementVO detail(Long batchId);

    SettlementVos.BatchSettlementVO confirm(Long batchId, FinanceRequests.SupplementConfirmRequest request);
}
