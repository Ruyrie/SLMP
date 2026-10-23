package com.zhimu.server.biz.stock.support;

import com.zhimu.server.biz.stock.vo.CostAllocationVO;
import com.zhimu.server.biz.stock.vo.StockInboundVO;
import com.zhimu.server.biz.stock.vo.StockLossVO;
import com.zhimu.server.biz.stock.vo.StockOutboundVO;
import com.zhimu.server.common.vo.QtyVO;

/**
 * 写接口的成功文案（App 只显示 message，文案按模块09 2.5 节与 App mock）。
 * 数量按包装写法：疫苗“50 瓶”、兽药“4 袋”、饲料“1,000 千克”。
 */
public final class StockMessages {

    private StockMessages() {
    }

    /** 饲料入库：“已入库 1,000 千克育肥全价料，已同步记一笔支出” */
    public static String feedInbound(StockInboundVO vo) {
        String text = "已入库 " + feedQty(vo.getQty()) + " " + vo.getMaterialName();
        return vo.isSyncFinance() ? text + "，已同步记一笔支出" : text;
    }

    /** 药品入库：“已入库 50 瓶口蹄疫灭活疫苗，已同步记一笔支出” */
    public static String drugInbound(StockInboundVO vo) {
        String text = "已入库 " + packQty(vo.getQty()) + " " + vo.getMaterialName();
        return vo.isSyncFinance() ? text + "，已同步记一笔支出" : text;
    }

    /** 饲料其他出库：“已登记其他出库 200 千克保育料” */
    public static String feedOtherOut(StockOutboundVO vo) {
        return "已登记其他出库 " + feedQty(vo.getQty()) + " " + vo.getMaterialName();
    }

    /** 领用 / 退货：“已登记领用 2 桶聚维酮碘溶液，成本已计入 2 个批次”“已登记退货 4 袋阿莫西林可溶性粉” */
    public static String drugOutbound(StockOutboundVO vo) {
        String qty = packQty(vo.getQty());
        if ("RETURN".equals(vo.getOutType())) {
            return "已登记退货 " + qty + " " + vo.getMaterialName();
        }
        String text = "已登记领用 " + qty + " " + vo.getMaterialName();
        int count = vo.getAllocations() == null ? 0 : vo.getAllocations().size();
        if (count == 1) {
            CostAllocationVO.Row row = vo.getAllocations().get(0);
            return row.getBatchNo() == null ? text : text + "，成本已计入 " + row.getBatchNo();
        }
        if (count > 1) {
            return text + "，成本已计入 " + count + " 个批次";
        }
        return text;
    }

    /** 报损：“已登记报损 10 千克育肥全价料” */
    public static String feedLoss(StockLossVO vo) {
        return "已登记报损 " + feedQty(vo.getQty()) + " " + vo.getMaterialName();
    }

    /** 报废：全部清零时“已登记报废，口蹄疫灭活疫苗 2025047 结存已清零”，部分“已登记报废 100 头份” */
    public static String drugScrap(StockLossVO vo) {
        boolean cleared = vo.getRemaining() == null || vo.getRemaining().getValue() == null
                || vo.getRemaining().getValue().signum() == 0;
        if (cleared) {
            return "已登记报废，" + vo.getMaterialName() + " " + vo.getLotNo() + " 结存已清零";
        }
        return "已登记报废 " + packQty(vo.getQty());
    }

    private static String feedQty(QtyVO qty) {
        return qty == null || qty.getValue() == null ? "" : StockQty.plain(qty.getValue());
    }

    private static String packQty(QtyVO qty) {
        if (qty == null || qty.getValue() == null) {
            return "";
        }
        if (qty.getPackUnit() != null && qty.getPackCount() != null && qty.getPackCount() > 0) {
            return qty.getPackCount() + " " + qty.getPackUnit();
        }
        return StockQty.plain(qty.getValue()) + " " + (qty.getUnitText() == null ? "" : qty.getUnitText());
    }
}
