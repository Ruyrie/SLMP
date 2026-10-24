package com.zhimu.server.admin.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.content.entity.AiCorrectionRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** AI 更正说明（t_ai_correction）的管理端写入。 */
public interface AiCorrectionAdminMapper extends BaseMapper<AiCorrectionRow> {

    @Select("SELECT * FROM t_ai_correction WHERE message_id = #{messageId} AND published_flag = 1 LIMIT 1")
    AiCorrectionRow selectPublished(@Param("messageId") Long messageId);

    @Select("SELECT * FROM t_ai_correction WHERE message_id = #{messageId} ORDER BY id DESC LIMIT 1")
    AiCorrectionRow selectLatest(@Param("messageId") Long messageId);

    @Select("SELECT * FROM t_ai_correction WHERE id = #{id}")
    AiCorrectionRow selectByIdRow(@Param("id") Long id);
}
