package com.zhimu.server.biz.stock.service;

import com.zhimu.server.biz.stock.request.DrugOutboundRequest;
import com.zhimu.server.biz.stock.request.FeedOtherOutRequest;
import com.zhimu.server.biz.stock.request.OutboundUpdateRequest;
import com.zhimu.server.biz.stock.request.VoidRequest;
import com.zhimu.server.biz.stock.vo.CostAllocationVO;
import com.zhimu.server.biz.stock.vo.StockOutboundVO;

import java.math.BigDecimal;
import java.util.List;

/** 出库单：饲料其他出库、药品领用 / 退货、详情、修改、作废、分摊预览、最近往来单位（M12、M20–M23、M32、M33）。 */
public interface StockOutboundService {

    /** API-M20 饲料其他出库（要求幂等键） */
    StockOutboundVO createFeedOtherOut(FeedOtherOutRequest request, String requestId);

    /** API-M32 领用 / 退货（要求幂等键；批号过期 / 停用 42204、禁用药 42203） */
    StockOutboundVO createDrugOutbound(DrugOutboundRequest request, String requestId);

    /** API-M21 出库单详情（含分摊） */
    StockOutboundVO get(Long id);

    /** API-M22 当天修改出库单；非当天 40307 */
    StockOutboundVO update(Long id, OutboundUpdateRequest request);

    /** API-M23 当天作废出库单；非当天 40307 */
    void voidOutbound(Long id, VoidRequest request);

    /** API-M33 领用成本分摊预览 */
    CostAllocationVO allocationPreview(Long drugLotId, BigDecimal inputQty, String inputUnit, String targetType,
                                       Long batchId, Long penId);

    /** API-M12 最近供应商 / 退回对象（最多 5 个） */
    List<String> recentPartners(String kind, String keyword);
}
