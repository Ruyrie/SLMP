package com.zhimu.server.biz.stock.service;

import com.zhimu.server.biz.stock.vo.StockFlowItemVO;
import com.zhimu.server.common.api.PageVO;

/** 库存流水：饲料流水（API-M13）与兽药疫苗流水（API-M28）。 */
public interface StockFlowService {

    /** API-M13 饲料流水：flow_group ALL / INBOUND / FEED_USE / OTHER_OUT / LOSS / STOCKTAKE */
    PageVO<StockFlowItemVO> feedFlows(Long feedId, String flowGroup, int page, int size);

    /** API-M28 药苗流水：flow_group ALL / INBOUND / USE / RETURN / SCRAP */
    PageVO<StockFlowItemVO> drugFlows(Long drugLotId, Long drugId, String flowGroup, int page, int size);
}
