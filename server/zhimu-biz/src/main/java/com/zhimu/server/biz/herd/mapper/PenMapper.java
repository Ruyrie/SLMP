package com.zhimu.server.biz.herd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.herd.entity.Pen;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 栏舍 Mapper：单表用通用方法；存在性判断与在栏数原子增减用轻量 SQL。 */
public interface PenMapper extends BaseMapper<Pen> {

    @Select("SELECT COUNT(1) FROM t_pen WHERE farm_id = #{farmId} AND pen_code = #{penCode}")
    long countByCode(@Param("farmId") Long farmId, @Param("penCode") String penCode);

    /** 在栏数原子增减；结果为负时不更新，影响 0 行由调用方转 42205 */
    @Update("""
            UPDATE t_pen SET stock_count = stock_count + #{delta}, updated_at = #{now}
            WHERE id = #{penId} AND stock_count + #{delta} >= 0
            """)
    int addStock(@Param("penId") Long penId, @Param("delta") int delta, @Param("now") LocalDateTime now);
}
