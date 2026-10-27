package com.zhimu.server.biz.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.finance.entity.BatchSettlement;

/** BatchSettlement 单表访问。 */
public interface BatchSettlementMapper extends BaseMapper<BatchSettlement> {
    /** 先取得该批次唯一行的写锁，避免两个详情请求同时首次创建核算。 */
    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO t_batch_settlement (farm_id, batch_id, status, calc_at, created_at, updated_at)
            VALUES (#{farmId}, #{batchId}, 'RUNNING', NOW(), NOW(), NOW())
            ON DUPLICATE KEY UPDATE batch_id = VALUES(batch_id)
            """)
    int ensureExists(@org.apache.ibatis.annotations.Param("farmId") Long farmId,
                     @org.apache.ibatis.annotations.Param("batchId") Long batchId);
}
