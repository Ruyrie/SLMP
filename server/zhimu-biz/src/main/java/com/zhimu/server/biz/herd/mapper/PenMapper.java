package com.zhimu.server.biz.herd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.herd.entity.Pen;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 栏舍 Mapper：单表用通用方法；存在性判断用轻量查询。 */
public interface PenMapper extends BaseMapper<Pen> {

    @Select("SELECT COUNT(1) FROM t_pen WHERE farm_id = #{farmId} AND pen_code = #{penCode}")
    long countByCode(@Param("farmId") Long farmId, @Param("penCode") String penCode);
}
