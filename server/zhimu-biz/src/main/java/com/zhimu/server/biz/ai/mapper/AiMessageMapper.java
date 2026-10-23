package com.zhimu.server.biz.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.ai.entity.AiMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI 问答轮次（t_ai_message）。
 */
public interface AiMessageMapper extends BaseMapper<AiMessage> {

    @Select("SELECT COALESCE(MAX(round_no), 0) FROM t_ai_message WHERE session_id = #{sessionId}")
    int maxRoundNo(@Param("sessionId") Long sessionId);

    @Select("SELECT * FROM t_ai_message WHERE user_id = #{userId} AND client_msg_id = #{clientMsgId} LIMIT 1")
    AiMessage selectByClientMsgId(@Param("userId") Long userId, @Param("clientMsgId") String clientMsgId);

    /** 本轮之前最近 limit 轮已回答的问答，按轮次倒序 */
    @Select("SELECT * FROM t_ai_message WHERE session_id = #{sessionId} AND round_no < #{roundNo} "
            + "AND status = 'ANSWERED' ORDER BY round_no DESC LIMIT #{limit}")
    List<AiMessage> selectContextBefore(@Param("sessionId") Long sessionId, @Param("roundNo") Integer roundNo,
                                        @Param("limit") int limit);

    /** 一轮之前最近 limit 轮（含所有状态），用于按轮次分页 */
    @Select("SELECT * FROM t_ai_message WHERE session_id = #{sessionId} AND round_no < #{beforeRoundNo} "
            + "ORDER BY round_no DESC LIMIT #{limit}")
    List<AiMessage> selectBeforeRound(@Param("sessionId") Long sessionId,
                                      @Param("beforeRoundNo") Integer beforeRoundNo,
                                      @Param("limit") int limit);

    @Select("SELECT * FROM t_ai_message WHERE session_id = #{sessionId} AND round_no > #{afterRoundNo} "
            + "ORDER BY round_no LIMIT #{limit}")
    List<AiMessage> selectAfterRound(@Param("sessionId") Long sessionId,
                                     @Param("afterRoundNo") Integer afterRoundNo,
                                     @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_ai_message WHERE session_id = #{sessionId} AND round_no < #{beforeRoundNo}")
    long countBeforeRound(@Param("sessionId") Long sessionId, @Param("beforeRoundNo") Integer beforeRoundNo);

    @Select("SELECT COUNT(*) FROM t_ai_message WHERE session_id = #{sessionId} AND round_no > #{afterRoundNo}")
    long countAfterRound(@Param("sessionId") Long sessionId, @Param("afterRoundNo") Integer afterRoundNo);

    /** keyword 匹配任一提问文字的会话（K01） */
    @Select("SELECT DISTINCT session_id FROM t_ai_message WHERE user_id = #{userId} "
            + "AND question_text LIKE CONCAT('%', #{keyword}, '%')")
    List<Long> selectSessionIdsByQuestion(@Param("userId") Long userId, @Param("keyword") String keyword);
}
