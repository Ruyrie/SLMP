package com.zhimu.server.biz.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.stock.entity.StockFlow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 库存流水 Mapper。 */
public interface StockFlowMapper extends BaseMapper<StockFlow> {

    /** 来源记录的有效流水（修改申请审核回写时更新数量与金额） */
    @Select("SELECT * FROM t_stock_flow WHERE biz_type = #{bizType} AND biz_id = #{bizId} "
            + "AND status = 'NORMAL' ORDER BY origin_flow_id, id")
    List<StockFlow> selectValidByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /** 入库单之后是否已有出库流水：有则修改必须走修改申请 */
    @Select("SELECT COUNT(1) FROM t_stock_flow WHERE direction = 'OUT' AND status = 'NORMAL' "
            + "AND origin_flow_id > #{flowId} AND feed_id = #{feedId}")
    long countOutboundAfterFeed(@Param("flowId") Long flowId, @Param("feedId") Long feedId);

    @Select("SELECT COUNT(1) FROM t_stock_flow WHERE direction = 'OUT' AND status = 'NORMAL' "
            + "AND origin_flow_id > #{flowId} AND drug_lot_id = #{drugLotId}")
    long countOutboundAfterLot(@Param("flowId") Long flowId, @Param("drugLotId") Long drugLotId);
}
