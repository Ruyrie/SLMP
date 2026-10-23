package com.zhimu.server.biz.stock.service;

import com.zhimu.server.biz.stock.request.DrugInboundRequest;
import com.zhimu.server.biz.stock.request.FeedInboundRequest;
import com.zhimu.server.biz.stock.request.InboundUpdateRequest;
import com.zhimu.server.biz.stock.request.VoidRequest;
import com.zhimu.server.biz.stock.vo.StockInboundVO;

/** 入库单：饲料入库、药品入库、详情、修改、作废（API-M08 / M09 / M10 / M11 / M30）。 */
public interface StockInboundService {

    /** API-M08 饲料入库（要求幂等键） */
    StockInboundVO createFeedInbound(FeedInboundRequest request, String requestId);

    /** API-M30 兽药疫苗入库（要求幂等键；批号过期 / 停用、禁用药拦截） */
    StockInboundVO createDrugInbound(DrugInboundRequest request, String requestId);

    /** API-M09 入库单详情 */
    StockInboundVO get(Long id);

    /** API-M10 修改未出库入库单；已发生出库抛 40307 */
    StockInboundVO update(Long id, InboundUpdateRequest request);

    /** API-M11 作废入库单；已发生出库抛 40307 */
    void voidInbound(Long id, VoidRequest request);
}
