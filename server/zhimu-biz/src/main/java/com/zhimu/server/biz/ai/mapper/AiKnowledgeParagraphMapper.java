package com.zhimu.server.biz.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.ai.entity.AiKnowledgeParagraph;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识段落（t_ai_knowledge_paragraph）。
 */
public interface AiKnowledgeParagraphMapper extends BaseMapper<AiKnowledgeParagraph> {

    @Select("SELECT * FROM t_ai_knowledge_paragraph WHERE knowledge_id = #{knowledgeId} ORDER BY para_no")
    List<AiKnowledgeParagraph> selectByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    @Select("SELECT * FROM t_ai_knowledge_paragraph WHERE knowledge_id = #{knowledgeId} AND para_key = #{paraKey} "
            + "ORDER BY id DESC LIMIT 1")
    AiKnowledgeParagraph selectByKey(@Param("knowledgeId") Long knowledgeId, @Param("paraKey") String paraKey);

    @Select("<script>SELECT * FROM t_ai_knowledge_paragraph WHERE para_type NOT IN ('IMAGE', 'VIDEO') "
            + "AND knowledge_id IN <foreach collection='knowledgeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY knowledge_id, para_no</script>")
    List<AiKnowledgeParagraph> selectByKnowledgeIds(@Param("knowledgeIds") List<Long> knowledgeIds);
}
