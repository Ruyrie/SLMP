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

    /**
     * 按来源去重键取（含已逻辑删除的行）：唯一键 uk_todo_dedup 覆盖已删除行，
     * 来源幂等写入使用当前读，避免并发补货事务的旧快照漏掉已生成的预警。
     */
    @Select("""
            SELECT *
            FROM t_todo
            WHERE farm_id = #{farmId} AND todo_type = #{todoType} AND dedup_key = #{dedupKey}
            LIMIT 1 FOR UPDATE
            """)
    Todo selectByDedupIncludingDeleted(@Param("farmId") Long farmId, @Param("todoType") String todoType,
                                       @Param("dedupKey") String dedupKey);
}
