package com.zhimu.server.biz.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 修改申请（t_change_request）。分页与计数在 XML 中，其余为单表语句。
 * 本表被所有养殖场共用，跨养殖场查询（U03/U04、管理端队列、系统撤销）一律不依赖多租户条件。
 */
public interface ChangeRequestMapper extends BaseMapper<ChangeRequest> {

    @Select("SELECT * FROM t_change_request WHERE id = #{id}")
    ChangeRequest selectByIdAny(@Param("id") Long id);

    @Select("SELECT * FROM t_change_request WHERE biz_type = #{bizType} AND biz_id = #{bizId} "
            + "AND pending_flag = 1 LIMIT 1")
    ChangeRequest selectPending(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    @Select("SELECT COUNT(*) FROM t_change_request WHERE resubmit_of_id = #{resubmitOfId}")
    long countResubmitted(@Param("resubmitOfId") Long resubmitOfId);

    List<ChangeRequest> selectMine(@Param("submittedBy") Long submittedBy,
                                   @Param("status") String status,
                                   @Param("bizType") String bizType,
                                   @Param("farmId") Long farmId,
                                   @Param("keyword") String keyword,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    /** status 为 null 时排除 CANCELED，与列表默认过滤一致 */
    long countMine(@Param("submittedBy") Long submittedBy,
                   @Param("status") String status,
                   @Param("bizType") String bizType,
                   @Param("farmId") Long farmId,
                   @Param("keyword") String keyword);

    /** 指定状态的数量（tab_counts 用，忽略 status 筛选） */
    long countMineByStatus(@Param("submittedBy") Long submittedBy,
                           @Param("status") String status,
                           @Param("bizType") String bizType,
                           @Param("farmId") Long farmId,
                           @Param("keyword") String keyword);

    @Select("<script>SELECT COUNT(*) FROM t_change_request "
            + "WHERE submitted_by = #{userId} AND status = 'PENDING' "
            + "<if test='farmId != null'>AND farm_id = #{farmId}</if></script>")
    long countPendingBySubmitter(@Param("userId") Long userId, @Param("farmId") Long farmId);

    @Select("SELECT COUNT(*) FROM t_change_request WHERE status = 'PENDING'")
    long countPending();

    @Update("UPDATE t_change_request SET status = 'CANCELED', pending_flag = NULL, cancel_reason = #{reason}, "
            + "version = version + 1, updated_at = #{now} "
            + "WHERE submitted_by = #{userId} AND status = 'PENDING'")
    int cancelPendingBySubmitter(@Param("userId") Long userId, @Param("reason") String reason,
                                 @Param("now") LocalDateTime now);

    @Update("UPDATE t_change_request SET status = 'CANCELED', pending_flag = NULL, cancel_reason = #{reason}, "
            + "version = version + 1, updated_at = #{now} "
            + "WHERE farm_id = #{farmId} AND status = 'PENDING'")
    int cancelPendingByFarm(@Param("farmId") Long farmId, @Param("reason") String reason,
                            @Param("now") LocalDateTime now);

    /** 管理端通过：带状态与 version 条件占用申请，成功后 pending_flag 置 NULL */
    @Update("UPDATE t_change_request SET status = 'APPROVED', pending_flag = NULL, reviewed_by = #{adminId}, "
            + "reviewed_at = #{now}, review_comment = #{comment}, version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'PENDING' AND version = #{version}")
    int claimApprove(@Param("id") Long id, @Param("version") int version, @Param("adminId") Long adminId,
                     @Param("comment") String comment, @Param("now") LocalDateTime now);

    /** 管理端驳回：pending_flag 置 NULL */
    @Update("UPDATE t_change_request SET status = 'REJECTED', pending_flag = NULL, reviewed_by = #{adminId}, "
            + "reviewed_at = #{now}, reject_reason = #{reason}, version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'PENDING' AND version = #{version}")
    int markRejected(@Param("id") Long id, @Param("version") int version, @Param("adminId") Long adminId,
                     @Param("reason") String reason, @Param("now") LocalDateTime now);

    /** 通过后的重算结果与影响快照 */
    @Update("UPDATE t_change_request SET recalc_summary = #{summary}, recalc_count = #{count}, "
            + "impact_json = #{impactJson}, updated_at = #{now} WHERE id = #{id}")
    int updateResult(@Param("id") Long id, @Param("summary") String summary, @Param("count") Integer count,
                     @Param("impactJson") String impactJson, @Param("now") LocalDateTime now);
}
