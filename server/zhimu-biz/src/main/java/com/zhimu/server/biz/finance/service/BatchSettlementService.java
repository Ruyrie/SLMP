package com.zhimu.server.biz.finance.service;

import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.vo.SettlementVos;

/** 批次核算（API-E26–E28）。 */
public interface BatchSettlementService {

    SettlementVos.OptionPageVO options(FinanceQueries.SettlementQuery query);

    SettlementVos.BatchSettlementVO detail(Long batchId);

    /** 出栏写入/审核后同事务重算；显式养殖场用于管理员审核上下文。 */
    void recalculateForSale(Long farmId, Long batchId);

    SettlementVos.BatchSettlementVO confirm(Long batchId, FinanceRequests.SupplementConfirmRequest request);
}
