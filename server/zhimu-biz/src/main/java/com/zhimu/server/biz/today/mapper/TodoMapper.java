package com.zhimu.server.biz.today.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.today.entity.Todo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 待办（t_todo）。跨养殖场查询由 Service 显式 TenantSkip 后带 farm_id 列表。 */
public interface TodoMapper extends BaseMapper<Todo> {

    /** 按 ID 取（含已逻辑删除的行），详情判权前先区分 40401 与 41001 */
    @Select("SELECT * FROM t_todo WHERE id = #{id} LIMIT 1")
    Todo selectIncludingDeleted(@Param("id") Long id);
}
