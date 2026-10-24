package com.zhimu.server.biz.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.health.entity.DisinfectPlan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 消毒计划 Mapper。 */
public interface DisinfectPlanMapper extends BaseMapper<DisinfectPlan> {

    @Select("SELECT * FROM t_disinfect_plan WHERE id = #{id} FOR UPDATE")
    DisinfectPlan selectForUpdate(@Param("id") Long id);
}
