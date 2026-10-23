package com.zhimu.server.biz.herd.service;

import com.zhimu.server.biz.herd.query.StockCategoryQuery;
import com.zhimu.server.biz.herd.query.StockPenQuery;
import com.zhimu.server.biz.herd.vo.CategoryStockVO;
import com.zhimu.server.biz.herd.vo.MyPensVO;
import com.zhimu.server.biz.herd.vo.PenStockItemVO;
import com.zhimu.server.biz.herd.vo.ProductionOverviewVO;
import com.zhimu.server.biz.herd.vo.StockSummaryVO;
import com.zhimu.server.common.api.PageVO;

import java.util.List;

/** 模块04 生产页与存栏查询（API-L01–L05）。 */
public interface StockService {

    ProductionOverviewVO overview();

    MyPensVO myPens();

    StockSummaryVO summary();

    PageVO<PenStockItemVO> pens(StockPenQuery query);

    List<CategoryStockVO> categories(StockCategoryQuery query);
}
