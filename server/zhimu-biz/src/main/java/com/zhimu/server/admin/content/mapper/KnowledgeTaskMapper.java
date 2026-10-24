package com.zhimu.server.admin.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.content.entity.KnowledgeTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识补充任务（t_ai_knowledge_task）。
 */
public interface KnowledgeTaskMapper extends BaseMapper<KnowledgeTask> {

    @Select("SELECT * FROM t_ai_knowledge_task WHERE source_message_id = #{messageId} "
            + "AND status = 'PENDING' LIMIT 1")
    KnowledgeTask selectPendingBySource(@Param("messageId") Long messageId);

    @Select("SELECT COUNT(*) FROM t_ai_knowledge_task WHERE status = 'PENDING'")
    long countPending();

    @Select("SELECT * FROM t_ai_knowledge_task WHERE target_knowledge_id = #{knowledgeId} "
            + "AND status = 'PENDING' ORDER BY id")
    List<KnowledgeTask> selectPendingByTarget(@Param("knowledgeId") Long knowledgeId);
}
