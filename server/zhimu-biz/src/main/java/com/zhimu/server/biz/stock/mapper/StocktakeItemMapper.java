package com.zhimu.server.biz.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.stock.entity.StocktakeItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 盘点明细 Mapper。 */
public interface StocktakeItemMapper extends BaseMapper<StocktakeItem> {

    /** 按盘点单取明细：修改申请 U01 与审核重算共用，不依赖 lambda 条件 */
    @Select("SELECT * FROM t_stocktake_item WHERE stocktake_id = #{stocktakeId} ORDER BY id")
    List<StocktakeItem> selectByStocktakeId(@Param("stocktakeId") Long stocktakeId);
}
