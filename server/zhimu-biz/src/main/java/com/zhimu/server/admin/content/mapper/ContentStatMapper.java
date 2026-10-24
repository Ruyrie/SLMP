package com.zhimu.server.admin.content.mapper;

import com.zhimu.server.admin.content.vo.StatAggregates;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容运营统计查询：课程播放 / 完播、知识条目被 AI 引用与有用率、段落引用。
 * 只读跨表聚合，写操作仍在各自 Mapper。
 */
public interface ContentStatMapper {

    @Select("SELECT course_id, COUNT(*) AS plays, COUNT(DISTINCT user_id) AS viewers "
            + "FROM t_course_play_record WHERE started_at >= #{from} GROUP BY course_id")
    List<StatAggregates.CoursePlayAgg> selectPlayAggSince(@Param("from") LocalDateTime from);

    @Select("SELECT course_id, COUNT(*) AS plays, COUNT(DISTINCT user_id) AS viewers "
            + "FROM t_course_play_record GROUP BY course_id")
    List<StatAggregates.CoursePlayAgg> selectPlayAggAll();

    @Select("SELECT course_id, COUNT(*) AS finishes FROM t_course_progress "
            + "WHERE completed = 1 GROUP BY course_id")
    List<StatAggregates.CourseFinishAgg> selectFinishAgg();

    @Select("SELECT COUNT(DISTINCT user_id) FROM t_course_play_record WHERE started_at >= #{from}")
    Long countViewersSince(@Param("from") LocalDateTime from);

    @Select("SELECT COUNT(DISTINCT message_id) FROM t_ai_citation "
            + "WHERE knowledge_id = #{knowledgeId} AND created_at >= #{from}")
    Long countCitationMessages(@Param("knowledgeId") Long knowledgeId, @Param("from") LocalDateTime from);

    @Select("SELECT COUNT(DISTINCT message_id) FROM t_ai_citation "
            + "WHERE knowledge_id = #{knowledgeId} AND knowledge_version_no = #{versionNo} "
            + "AND created_at >= #{from}")
    Long countCitationMessagesByVersion(@Param("knowledgeId") Long knowledgeId,
                                        @Param("versionNo") Integer versionNo,
                                        @Param("from") LocalDateTime from);

    @Select("SELECT COUNT(DISTINCT CASE WHEN m.rating = 'USEFUL' THEN c.message_id END) AS useful, "
            + "COUNT(DISTINCT CASE WHEN m.rating IN ('USEFUL', 'USELESS') THEN c.message_id END) AS rated "
            + "FROM t_ai_citation c JOIN t_ai_message m ON m.id = c.message_id "
            + "WHERE c.knowledge_id = #{knowledgeId} AND c.created_at >= #{from}")
    StatAggregates.RatingAgg selectRating(@Param("knowledgeId") Long knowledgeId,
                                          @Param("from") LocalDateTime from);

    @Select("SELECT para_key, COUNT(DISTINCT message_id) AS citations FROM t_ai_citation "
            + "WHERE knowledge_id = #{knowledgeId} AND created_at >= #{from} GROUP BY para_key")
    List<StatAggregates.ParagraphCiteAgg> selectParagraphCites(@Param("knowledgeId") Long knowledgeId,
                                                               @Param("from") LocalDateTime from);

    @Select("SELECT id, name, status FROM t_course_category ORDER BY sort_no, id")
    List<StatAggregates.CategoryRef> selectContentCategories();

    @Select("<script>SELECT id, name, status FROM t_category WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<StatAggregates.CategoryRef> selectProductCategories(@Param("ids") List<Long> ids);
}
