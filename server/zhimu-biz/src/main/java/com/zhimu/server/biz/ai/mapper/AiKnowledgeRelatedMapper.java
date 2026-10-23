package com.zhimu.server.biz.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.ai.entity.AiKnowledgeRelated;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识相关条目（t_ai_knowledge_related）。
 */
public interface AiKnowledgeRelatedMapper extends BaseMapper<AiKnowledgeRelated> {

    @Select("SELECT * FROM t_ai_knowledge_related WHERE knowledge_id = #{knowledgeId} ORDER BY sort_no, id")
    List<AiKnowledgeRelated> selectByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
}
