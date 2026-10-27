package com.zhimu.server.biz.account.mapper;

import com.zhimu.server.biz.today.entity.PushDevice;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 注销专用的物理清理：调用方先锁定该用户并校验 CANCELLED，SQL 再次限制用户状态。 */
public interface AccountPersonalCleanupMapper {

    String CANCELLED = " AND EXISTS (SELECT 1 FROM t_user u WHERE u.id=#{userId} AND u.status='CANCELLED')";

    @Select("SELECT * FROM t_push_device WHERE user_id=#{userId} ORDER BY id FOR UPDATE")
    List<PushDevice> lockDevices(@Param("userId") Long userId);

    @Update("UPDATE t_push_device SET status='UNBOUND', alias_bound=0, jpush_alias=NULL, updated_at=#{now} "
            + "WHERE id=#{deviceId} AND user_id=#{userId}" + CANCELLED)
    int unbindDevice(@Param("userId") Long userId, @Param("deviceId") Long deviceId,
                     @Param("now") LocalDateTime now);

    @Delete("DELETE FROM t_push_record WHERE user_id=#{userId}" + CANCELLED)
    int deletePushRecords(@Param("userId") Long userId);

    @Delete("DELETE FROM t_message WHERE user_id=#{userId}" + CANCELLED)
    int deleteMessages(@Param("userId") Long userId);

    @Delete("DELETE FROM t_reminder_setting WHERE user_id=#{userId}" + CANCELLED)
    int deleteReminderSettings(@Param("userId") Long userId);

    @Delete("DELETE FROM t_course_play_record WHERE user_id=#{userId}" + CANCELLED)
    int deleteCoursePlayRecords(@Param("userId") Long userId);

    @Delete("DELETE FROM t_course_progress WHERE user_id=#{userId}" + CANCELLED)
    int deleteCourseProgress(@Param("userId") Long userId);

    @Update("UPDATE t_recovery_code SET code_hash='REDACTED_ACCOUNT_CANCELLED', status='REVOKED', "
            + "live_flag=NULL, revoke_reason='ACCOUNT_CANCELLED', revoked_at=COALESCE(revoked_at,#{now}), "
            + "updated_at=#{now} WHERE user_id=#{userId}" + CANCELLED)
    int redactRecoveryCodes(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("UPDATE t_user SET avatar_file_id=NULL, last_farm_id=NULL, updated_at=#{now} "
            + "WHERE id=#{userId} AND status='CANCELLED'")
    int clearPersonalProfileLinks(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("SELECT id FROM t_ai_message WHERE user_id=#{userId}" + CANCELLED + " ORDER BY id")
    List<Long> selectAiMessageIds(@Param("userId") Long userId);

    @Delete("DELETE FROM t_biz_media WHERE biz_type='AI_MESSAGE' "
            + "AND biz_id IN (SELECT id FROM t_ai_message WHERE user_id=#{userId})" + CANCELLED)
    int deleteAiMediaLinks(@Param("userId") Long userId);

    @Delete("DELETE FROM t_ai_citation WHERE message_id IN "
            + "(SELECT id FROM t_ai_message WHERE user_id=#{userId})" + CANCELLED)
    int deleteAiCitations(@Param("userId") Long userId);

    @Delete("DELETE FROM t_ai_correction WHERE message_id IN "
            + "(SELECT id FROM t_ai_message WHERE user_id=#{userId})" + CANCELLED)
    int deleteAiCorrections(@Param("userId") Long userId);

    /** 审核留痕保留，删除其中复制的个人问答正文；已经沉淀的知识/任务不是个人会话。 */
    @Update("UPDATE t_ai_qa_review SET spot_check_title=NULL, spot_check_content=NULL WHERE message_id IN "
            + "(SELECT id FROM t_ai_message WHERE user_id=#{userId})" + CANCELLED)
    int redactAiReviewSnapshots(@Param("userId") Long userId);

    @Update("UPDATE t_ai_knowledge_task SET source_message_id=NULL WHERE source_message_id IN "
            + "(SELECT id FROM t_ai_message WHERE user_id=#{userId})" + CANCELLED)
    int detachKnowledgeTaskSources(@Param("userId") Long userId);

    @Delete("DELETE FROM t_ai_message WHERE user_id=#{userId}" + CANCELLED)
    int deleteAiMessages(@Param("userId") Long userId);

    @Delete("DELETE FROM t_ai_session WHERE user_id=#{userId}" + CANCELLED)
    int deleteAiSessions(@Param("userId") Long userId);

    @Update("UPDATE t_user SET phone=NULL, updated_at=#{now} WHERE id=#{userId} AND status='CANCELLED' "
            + "AND cancelled_at=#{cancelledAt} AND phone=#{phone}")
    int releasePhone(@Param("userId") Long userId, @Param("cancelledAt") LocalDateTime cancelledAt,
                     @Param("phone") String phone, @Param("now") LocalDateTime now);
}
