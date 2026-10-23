package com.zhimu.server.biz.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.health.entity.BatchWithdrawal;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 批次休药汇总 Mapper。 */
public interface BatchWithdrawalMapper extends BaseMapper<BatchWithdrawal> {

    /** 重算前加锁读取，保证同批次并发登记串行（2.10） */
    @Select("SELECT * FROM t_batch_withdrawal WHERE batch_id = #{batchId} FOR UPDATE")
    BatchWithdrawal selectForUpdate(@Param("batchId") Long batchId);

    @Select("SELECT * FROM t_batch_withdrawal WHERE batch_id = #{batchId}")
    BatchWithdrawal selectByBatchId(@Param("batchId") Long batchId);
}
