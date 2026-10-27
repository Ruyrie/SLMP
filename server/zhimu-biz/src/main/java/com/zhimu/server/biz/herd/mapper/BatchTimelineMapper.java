package com.zhimu.server.biz.herd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.herd.entity.BatchTimeline;
import org.apache.ibatis.annotations.Insert;

/** 批次时间线 Mapper。 */
public interface BatchTimelineMapper extends BaseMapper<BatchTimeline> {
    /** 数量修正先撤回原出栏，再用同一业务编号重建时间线，恢复软删除记录。 */
    @Insert("""
            INSERT INTO t_batch_timeline
                (farm_id, batch_id, pen_id, record_type, biz_type, biz_id, biz_date, occurred_at,
                 title, subtitle, detail_json, operator_user_id, deleted, created_at, updated_at)
            VALUES (#{farmId}, #{batchId}, #{penId}, #{recordType}, #{bizType}, #{bizId}, #{bizDate},
                    #{occurredAt}, #{title}, #{subtitle}, #{detailJson}, #{operatorUserId}, 0, NOW(), NOW())
            ON DUPLICATE KEY UPDATE pen_id = VALUES(pen_id), biz_date = VALUES(biz_date),
                occurred_at = VALUES(occurred_at), title = VALUES(title), subtitle = VALUES(subtitle),
                detail_json = VALUES(detail_json), operator_user_id = VALUES(operator_user_id),
                deleted = 0, updated_at = NOW()
            """)
    int upsertSaleTimeline(BatchTimeline row);
}
