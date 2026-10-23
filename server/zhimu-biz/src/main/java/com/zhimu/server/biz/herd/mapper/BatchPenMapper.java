package com.zhimu.server.biz.herd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.herd.entity.BatchPen;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 批次栏舍分布 Mapper：在栏数用条件原子更新，影响 0 行由 Service 转 42205。 */
public interface BatchPenMapper extends BaseMapper<BatchPen> {

    /** 转出 / 死淘原子扣减：在栏数足够时才更新；降为 0 时写 last_out_date */
    @Update("""
            UPDATE t_batch_pen
            SET stock_count = stock_count - #{quantity},
                last_out_date = IF(stock_count - #{quantity} = 0, #{eventDate}, last_out_date),
                updated_at = #{now}
            WHERE batch_id = #{batchId} AND pen_id = #{penId} AND stock_count >= #{quantity}
            """)
    int deduct(@Param("batchId") Long batchId, @Param("penId") Long penId, @Param("quantity") int quantity,
               @Param("eventDate") LocalDate eventDate, @Param("now") LocalDateTime now);

    /** 转入 / 放回原子增加：从 0 变正时写 first_in_date */
    @Update("""
            UPDATE t_batch_pen
            SET stock_count = stock_count + #{quantity},
                first_in_date = IF(stock_count = 0, #{eventDate}, first_in_date),
                last_out_date = NULL,
                updated_at = #{now}
            WHERE batch_id = #{batchId} AND pen_id = #{penId}
            """)
    int addStock(@Param("batchId") Long batchId, @Param("penId") Long penId, @Param("quantity") int quantity,
                 @Param("eventDate") LocalDate eventDate, @Param("now") LocalDateTime now);

    @Select("SELECT COALESCE(SUM(stock_count), 0) FROM t_batch_pen WHERE batch_id = #{batchId}")
    int sumStockOfBatch(@Param("batchId") Long batchId);
}
