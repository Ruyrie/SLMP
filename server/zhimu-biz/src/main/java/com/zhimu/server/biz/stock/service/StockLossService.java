package com.zhimu.server.biz.stock.service;

import com.zhimu.server.biz.stock.request.DrugScrapRequest;
import com.zhimu.server.biz.stock.request.FeedLossRequest;
import com.zhimu.server.biz.stock.vo.StockLossVO;

/** 报损与报废（API-M18 / M19 / M31）：报损只针对饲料、日期固定今天；报废只针对药品批号。 */
public interface StockLossService {

    /** API-M18 登记报损（要求幂等键）；数量超过结存 42201 */
    StockLossVO createFeedLoss(FeedLossRequest request, String requestId);

    /** API-M31 登记报废（要求幂等键）；stock_version 不一致 40902 */
    StockLossVO createDrugScrap(DrugScrapRequest request, String requestId);

    /** API-M19 报损 / 报废详情 */
    StockLossVO get(Long id);
}
