package com.zhimu.server.biz.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.stock.entity.StockOutbound;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 手工出库单 Mapper。 */
public interface StockOutboundMapper extends BaseMapper<StockOutbound> {
    @Select("SELECT * FROM t_stock_outbound WHERE id=#{id} AND farm_id=#{farmId} FOR UPDATE")
    StockOutbound selectForUpdate(@Param("id") Long id, @Param("farmId") Long farmId);
}
