package com.zhimu.server.biz.stock.mapper;

import com.zhimu.server.biz.stock.query.PenBatchRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 栏舍在栏批次只读查询（临时桥接）。
 * <p>
 * 按栏舍分摊需要“每个批次在该栏舍的存栏数”，模块04 的 {@code PenQueryService} 当前只提供栏舍与总存栏、
 * {@code BatchQueryService} 只提供批次的栏舍 ID 列表，都没有单栏存栏明细。这里只读 t_batch_pen 的
 * pen_id + stock_count，不写、不做业务判断；模块04 提供 {@code listStockedBatches} 后改调对方 API 并删除本 Mapper。
 */
public interface StockPenBatchMapper {

    @Select("""
            SELECT batch_id, stock_count
              FROM t_batch_pen
             WHERE farm_id = #{farmId} AND pen_id = #{penId} AND stock_count > 0
             ORDER BY batch_id
            """)
    List<PenBatchRow> listStockedBatches(@Param("farmId") Long farmId, @Param("penId") Long penId);
}
