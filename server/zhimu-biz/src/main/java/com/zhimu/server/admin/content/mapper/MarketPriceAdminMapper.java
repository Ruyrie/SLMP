package com.zhimu.server.admin.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.finance.entity.MarketPrice;

/** 管理端对 t_market_price 的读写（与模块10 共用表，用户端只读 PUBLISHED）。 */
public interface MarketPriceAdminMapper extends BaseMapper<MarketPrice> {
}
