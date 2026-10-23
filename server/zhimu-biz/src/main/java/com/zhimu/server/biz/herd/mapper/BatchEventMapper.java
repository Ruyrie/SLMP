package com.zhimu.server.biz.herd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.herd.entity.BatchEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 存栏变动流水 Mapper。 */
public interface BatchEventMapper extends BaseMapper<BatchEvent> {

    /** API-L26：本人按 Idempotency-Key 查询（同一请求编号只应有一条成功记录） */
    @Select("""
            SELECT * FROM t_batch_event
            WHERE created_by = #{userId} AND client_request_id = #{requestKey} AND deleted = 0
            ORDER BY id DESC LIMIT 1
            """)
    BatchEvent selectByRequest(@Param("userId") Long userId, @Param("requestKey") String requestKey);

    /** 批次的入栏流水（用于 entry_event_id 与入栏信息展示） */
    @Select("""
            SELECT * FROM t_batch_event
            WHERE batch_id = #{batchId} AND event_type = 'ENTRY' AND deleted = 0
            ORDER BY event_date ASC, id ASC LIMIT 1
            """)
    BatchEvent selectEntry(@Param("batchId") Long batchId);
}
