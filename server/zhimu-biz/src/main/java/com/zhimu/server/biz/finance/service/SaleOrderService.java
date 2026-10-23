package com.zhimu.server.biz.finance.service;

import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.vo.SaleOrderVos;

import java.util.List;

/** 出栏单与收款（API-E14–E23）。 */
public interface SaleOrderService {

    List<SaleOrderVos.BatchOptionVO> batchOptions();

    SaleOrderVos.PrefillVO prefill(Long batchId, String saleDate);

    SaleOrderVos.CreatedVO create(FinanceRequests.SaleOrderRequest request, String idempotencyKey);

    SaleOrderVos.OrderPageVO list(FinanceQueries.SaleOrderQuery query);

    SaleOrderVos.ReceivableVO receivables(FinanceQueries.ReceivableQuery query);

    SaleOrderVos.DetailVO detail(Long id);

    SaleOrderVos.DetailVO update(Long id, FinanceRequests.SaleOrderRequest request);

    SaleOrderVos.DetailVO voidOrder(Long id, FinanceRequests.SaleOrderVoidRequest request);

    SaleOrderVos.DetailVO addReceipt(Long id, FinanceRequests.SaleReceiptRequest request, String idempotencyKey);

    SaleOrderVos.ChangePreviewVO preview(Long id, FinanceRequests.SaleChangePreviewRequest request);
}

