package com.zhimu.server.biz.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.ai.entity.AiCorrection;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI 更正说明（t_ai_correction）。
 */
public interface AiCorrectionMapper extends BaseMapper<AiCorrection> {

    @Select("SELECT * FROM t_ai_correction WHERE message_id = #{messageId} AND status = 'PUBLISHED' "
            + "ORDER BY published_at DESC LIMIT 1")
    AiCorrection selectPublishedByMessageId(@Param("messageId") Long messageId);

    @Select("<script>SELECT * FROM t_ai_correction WHERE status = 'PUBLISHED' AND message_id IN "
            + "<foreach collection='messageIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY message_id, published_at DESC</script>")
    List<AiCorrection> selectPublishedByMessageIds(@Param("messageIds") List<Long> messageIds);
}
