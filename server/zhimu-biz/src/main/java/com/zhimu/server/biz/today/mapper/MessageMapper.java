package com.zhimu.server.biz.today.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.today.entity.Message;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 消息（t_message）。删除走逻辑删除，撤销删除需读取含已删除的行。 */
public interface MessageMapper extends BaseMapper<Message> {

    /** 按 ID + 接收人取（含已删除），用于删除幂等与 60 秒撤销 */
    @Select("SELECT * FROM t_message WHERE id = #{id} AND user_id = #{userId} LIMIT 1")
    Message selectIncludingDeleted(@Param("id") Long id, @Param("userId") Long userId);

    /** 撤销删除（逻辑删除字段不能用实体更新，直接写 SQL） */
    @Update("UPDATE t_message SET deleted = 0, deleted_at = NULL, updated_at = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId} AND deleted = 1")
    int restoreDeleted(@Param("id") Long id, @Param("userId") Long userId);

    /** 逻辑删除（MyBatis-Plus 的 updateById 不会写 deleted 字段） */
    @Update("UPDATE t_message SET deleted = 1, deleted_at = #{deletedAt}, updated_at = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId} AND deleted = 0")
    int markDeleted(@Param("id") Long id, @Param("userId") Long userId,
                    @Param("deletedAt") java.time.LocalDateTime deletedAt);
}
