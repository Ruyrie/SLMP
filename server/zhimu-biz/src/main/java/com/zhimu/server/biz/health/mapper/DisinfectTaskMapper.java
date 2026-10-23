package com.zhimu.server.biz.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.health.entity.DisinfectTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 消毒任务 Mapper。 */
public interface DisinfectTaskMapper extends BaseMapper<DisinfectTask> {

    @Select("SELECT * FROM t_disinfect_task WHERE id = #{id} FOR UPDATE")
    DisinfectTask selectForUpdate(@Param("id") Long id);
}
