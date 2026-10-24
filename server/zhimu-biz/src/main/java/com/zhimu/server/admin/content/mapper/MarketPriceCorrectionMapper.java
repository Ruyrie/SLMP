package com.zhimu.server.admin.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.content.entity.MarketPriceCorrection;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 行情更正记录（t_market_price_correction）。 */
public interface MarketPriceCorrectionMapper extends BaseMapper<MarketPriceCorrection> {

    @Select("SELECT * FROM t_market_price_correction WHERE price_id = #{priceId} ORDER BY id DESC")
    List<MarketPriceCorrection> selectByPriceId(@Param("priceId") Long priceId);

    @Select("SELECT * FROM t_market_price_correction WHERE price_id = #{priceId} AND pending_flag = 1 LIMIT 1")
    MarketPriceCorrection selectPending(@Param("priceId") Long priceId);
}
