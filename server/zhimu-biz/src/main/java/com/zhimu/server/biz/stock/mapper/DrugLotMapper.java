package com.zhimu.server.biz.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.stock.entity.DrugLot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药品批号 Mapper。结存更新用带版本条件的单条 UPDATE（乐观锁 + 调用方重试），
 * 任何出库都由 SQL 的 WHERE 保证结存不被扣成负数。
 */
public interface DrugLotMapper extends BaseMapper<DrugLot> {

    @Update("""
            UPDATE t_drug_lot
               SET stock_qty = #{qty}, stock_amount = #{amount}, avg_price = #{avgPrice},
                   version = version + 1, updated_at = #{now}
             WHERE id = #{id} AND version = #{version}
            """)
    int updateStock(@Param("id") Long id, @Param("version") Integer version, @Param("qty") BigDecimal qty,
                    @Param("amount") BigDecimal amount, @Param("avgPrice") BigDecimal avgPrice,
                    @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_drug_lot
               SET inbound_count = inbound_count + 1, expire_date = #{expireDate}, updated_at = #{now}
             WHERE id = #{id}
            """)
    int touchInbound(@Param("id") Long id, @Param("expireDate") java.time.LocalDate expireDate,
                     @Param("now") LocalDateTime now);
}
