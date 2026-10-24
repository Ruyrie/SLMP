package com.zhimu.server.admin.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.content.entity.KnowledgeVersion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识版本（t_ai_knowledge_version）。版本号由事务内最大值 + 1 生成，唯一键兜底。
 */
public interface KnowledgeVersionMapper extends BaseMapper<KnowledgeVersion> {

    @Select("SELECT * FROM t_ai_knowledge_version WHERE knowledge_id = #{knowledgeId} "
            + "AND status = 'PUBLISHED' ORDER BY version_no DESC")
    List<KnowledgeVersion> selectPublished(@Param("knowledgeId") Long knowledgeId);

    @Select("SELECT * FROM t_ai_knowledge_version WHERE knowledge_id = #{knowledgeId} "
            + "AND draft_flag = 1 LIMIT 1")
    KnowledgeVersion selectDraft(@Param("knowledgeId") Long knowledgeId);

    @Select("SELECT * FROM t_ai_knowledge_version WHERE knowledge_id = #{knowledgeId} "
            + "AND version_no = #{versionNo} LIMIT 1")
    KnowledgeVersion selectByVersionNo(@Param("knowledgeId") Long knowledgeId,
                                       @Param("versionNo") Integer versionNo);

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM t_ai_knowledge_version WHERE knowledge_id = #{knowledgeId}")
    Integer selectMaxVersionNo(@Param("knowledgeId") Long knowledgeId);

    @Select("SELECT * FROM t_ai_knowledge_version WHERE id = #{id}")
    KnowledgeVersion selectByIdForUpdate(@Param("id") Long id);
}
