package com.zhimu.server.biz.finance.service;

import com.zhimu.server.biz.finance.entity.MarketPrice;
import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.vo.MarketVos;
import com.zhimu.server.biz.finance.vo.OverviewVos;
import com.zhimu.server.common.api.PageVO;

import java.time.LocalDate;

/** 行情（API-E30–E32）与登记预填取价（API-E15）。 */
public interface MarketPriceService {

    MarketVos.FilterVO filters();

    MarketVos.TrendVO trend(FinanceQueries.MarketPriceQuery query);

    PageVO<MarketVos.DailyPriceVO> daily(FinanceQueries.MarketPriceQuery query);

    /** 养殖场所在省份代码（未匹配时取发布配置第一个省份） */
    String defaultProvinceCode(Long farmId);

    /** 养殖场主要在养品类（无在养批次时取注册主要品类） */
    Long defaultCategoryId(Long farmId);

    /** 最新一条价格（含当天及以前）；没有返回 null */
    MarketPrice latest(Long farmId, Long categoryId, String priceType, LocalDate date);

    /** E01 行情卡；没有价格返回 null */
    OverviewVos.PriceCard priceCard(Long farmId);

    /** E15 预填行情；没有价格返回 null */
    com.zhimu.server.biz.finance.vo.SaleOrderVos.MarketPrice prefillPrice(Long farmId, Long categoryId,
                                                                          LocalDate date);
}
