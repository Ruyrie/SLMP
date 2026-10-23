package com.zhimu.server.biz.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.stock.entity.BatchMaterialCost;
import com.zhimu.server.biz.stock.query.CostTypeSum;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 批次物资成本明细 Mapper。 */
public interface BatchMaterialCostMapper extends BaseMapper<BatchMaterialCost> {

    /**
     * 按成本类型汇总某批次的 NORMAL 明细（模块10 读批次成本用）。
     * farm_id 由调用方显式给出，避免 finance 侧没有 FarmContext 时查不到数据。
     */
    @Select("""
            SELECT cost_type, COALESCE(SUM(amount), 0) AS amount
            FROM t_batch_material_cost
            WHERE farm_id = #{farmId} AND batch_id = #{batchId} AND status = 'NORMAL'
            GROUP BY cost_type
            """)
    List<CostTypeSum> sumByCostType(@Param("farmId") Long farmId, @Param("batchId") Long batchId);
}
