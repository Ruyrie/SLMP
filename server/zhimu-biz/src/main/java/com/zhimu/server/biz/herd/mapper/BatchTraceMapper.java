package com.zhimu.server.biz.herd.mapper;

import com.zhimu.server.biz.herd.vo.BatchTraceVO.Event;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/** Deliberately projects only public fields; every query is constrained by the resolved farm and batch. */
public interface BatchTraceMapper {
    @Select("""
        SELECT 'WEIGH' type, weigh_date date, '称重' name, NULL quantity,
               avg_weight, NULL withdrawal_end_date, updated_at
        FROM t_weigh_record WHERE farm_id = #{farmId} AND batch_id = #{batchId}
        UNION ALL
        SELECT 'IMMUNE', vaccinate_date, vaccine_name, quantity, NULL, NULL, updated_at
        FROM t_immune_record WHERE farm_id = #{farmId} AND batch_id = #{batchId} AND deleted = 0
        UNION ALL
        SELECT 'MEDICATION', first_date, drug_name, NULL, NULL, withdrawal_end_date, updated_at
        FROM t_medication_record WHERE farm_id = #{farmId} AND batch_id = #{batchId} AND deleted = 0
        UNION ALL
        SELECT 'SALE', sale_date, '出栏', quantity, avg_weight, NULL, updated_at
        FROM t_sale_order WHERE farm_id = #{farmId} AND batch_id = #{batchId} AND payment_status <> 'VOIDED'
        ORDER BY date DESC, type ASC LIMIT 200
        """)
    List<Event> events(@Param("farmId") Long farmId, @Param("batchId") Long batchId);
}
