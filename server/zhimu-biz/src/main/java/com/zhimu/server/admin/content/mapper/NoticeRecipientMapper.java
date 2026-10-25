package com.zhimu.server.admin.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.content.entity.NoticeRecipient;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 通知送达明细（t_notice_recipient）。 */
public interface NoticeRecipientMapper extends BaseMapper<NoticeRecipient> {

    @Select("SELECT COUNT(*) FROM t_notice_recipient r JOIN t_notice n ON n.id=r.notice_id "
            + "WHERE r.notice_id=#{noticeId} AND r.user_id=#{userId} AND n.deleted=0 AND n.status='SENT'")
    long countReadableNotice(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM t_notice_recipient r JOIN t_notice n ON n.id=r.notice_id "
            + "WHERE n.image_file_id=#{fileId} AND r.user_id=#{userId} AND n.deleted=0 AND n.status='SENT'")
    long countReadableImage(@Param("fileId") Long fileId, @Param("userId") Long userId);

    List<RoleStatRow> selectRoleStats(@Param("noticeId") Long noticeId);

    /** 导出时按通知批量取角色阅读统计 */
    List<RoleStatRow> selectRoleStatsBatch(@Param("noticeIds") List<Long> noticeIds);

    /** 撤回时把未读明细置 is_hidden = 1 */
    int markUnreadHidden(@Param("noticeId") Long noticeId);

    /** 批量插入，忽略唯一键冲突（续发不重复） */
    int insertIgnore(@Param("list") List<NoticeRecipient> list);

    /** 按 message_id 回写已读与推送点开 */
    int updateReadState(@Param("noticeId") Long noticeId);
}
