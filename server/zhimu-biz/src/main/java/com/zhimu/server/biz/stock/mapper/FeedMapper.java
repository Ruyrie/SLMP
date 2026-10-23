package com.zhimu.server.biz.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.stock.entity.Feed;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 饲料档案 Mapper。结存更新用带版本条件的单条 UPDATE（乐观锁 + 调用方重试），
 * 避免先读后写的丢失更新；返回 0 行表示版本已被他人推进。
 */
public interface FeedMapper extends BaseMapper<Feed> {

    @Update("""
            UPDATE t_feed
               SET stock_qty = #{qty}, stock_amount = #{amount}, avg_price = #{avgPrice},
                   is_unit_locked = 1, version = version + 1, updated_at = #{now}
             WHERE id = #{id} AND version = #{version}
            """)
    int updateStock(@Param("id") Long id, @Param("version") Integer version, @Param("qty") BigDecimal qty,
                    @Param("amount") BigDecimal amount, @Param("avgPrice") BigDecimal avgPrice,
                    @Param("now") LocalDateTime now);
}
