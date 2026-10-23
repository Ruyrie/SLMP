package com.zhimu.server.biz.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.ai.entity.AiCitation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI 引用来源（t_ai_citation）。
 */
public interface AiCitationMapper extends BaseMapper<AiCitation> {

    @Select("SELECT * FROM t_ai_citation WHERE message_id = #{messageId} ORDER BY seq_no")
    List<AiCitation> selectByMessageId(@Param("messageId") Long messageId);

    @Select("<script>SELECT * FROM t_ai_citation WHERE message_id IN "
            + "<foreach collection='messageIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY message_id, seq_no</script>")
    List<AiCitation> selectByMessageIds(@Param("messageIds") List<Long> messageIds);

    @Delete("DELETE FROM t_ai_citation WHERE message_id = #{messageId}")
    int deleteByMessageId(@Param("messageId") Long messageId);
}
