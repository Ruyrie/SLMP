package com.zhimu.server.biz.stock.service;

import com.zhimu.server.biz.stock.request.StocktakeConfirmRequest;
import com.zhimu.server.biz.stock.vo.StocktakeSheetVO;
import com.zhimu.server.biz.stock.vo.StocktakeVO;
import com.zhimu.server.common.api.PageVO;

/** 盘点：盘点表、确认盘点、盘点记录与详情（API-M14–M17）。 */
public interface StocktakeService {

    /** API-M14 盘点表：全部需要盘点的饲料，带 book_qty 与 stock_version */
    StocktakeSheetVO sheet();

    /** API-M15 确认盘点（要求幂等键）；任一项账面变化整单拒绝并返回最新账面 */
    StocktakeVO confirm(StocktakeConfirmRequest request, String requestId);

    /** API-M16 盘点记录：按盘点日期倒序，不含 items */
    PageVO<StocktakeVO> page(int page, int size);

    /** API-M17 盘点详情 */
    StocktakeVO get(Long id);
}
