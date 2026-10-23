package com.zhimu.server.biz.feeding.mapper;

import com.zhimu.server.biz.feeding.support.FeedBatchPenRow;
import com.zhimu.server.biz.feeding.support.FeedBatchRow;
import com.zhimu.server.biz.feeding.support.FeedMemberPenRow;
import com.zhimu.server.biz.feeding.support.FeedMemberRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 本模块只读引用模块02 成员/负责栏舍与模块04 批次栏舍分布。
 * 模块02、04 目前没有提供成员列表与按批次栏舍存栏的 api 接口，这里用只读 SQL 取数，后续可替换为跨模块 api 调用。
 */
public interface FeedRefMapper {

    /** 养殖场在场成员（所有者、场长、员工），按加入时间排序 */
    @Select("""
            SELECT m.id AS member_id, m.user_id, m.role, m.joined_at
            FROM t_farm_member m
            WHERE m.farm_id = #{farmId} AND m.active_flag = 1 AND m.status = 'ACTIVE'
            ORDER BY m.joined_at, m.id
            """)
    List<FeedMemberRow> activeMembers(@Param("farmId") Long farmId);

    /** 员工负责栏舍 */
    @Select("""
            SELECT member_id, user_id, pen_id
            FROM t_member_pen
            WHERE farm_id = #{farmId}
            ORDER BY pen_id, id
            """)
    List<FeedMemberPenRow> memberPens(@Param("farmId") Long farmId);

    /** 批次当前有在栏数的栏舍 */
    @Select("""
            SELECT pen_id, stock_count
            FROM t_batch_pen
            WHERE batch_id = #{batchId} AND stock_count > 0
            ORDER BY pen_id
            """)
    List<FeedBatchPenRow> batchPens(@Param("batchId") Long batchId);

    /** 批次的全部栏舍（含已转出的空栏），用于已结束批次的栏舍名展示 */
    @Select("""
            SELECT DISTINCT pen_id
            FROM t_batch_pen
            WHERE batch_id = #{batchId}
            ORDER BY pen_id
            """)
    List<Long> batchPenIds(@Param("batchId") Long batchId);

    /** 有在养批次的养殖场 ID（定时任务跨养殖场扫描用） */
    @Select("""
            SELECT DISTINCT farm_id
            FROM t_batch
            WHERE status = 'ACTIVE' AND deleted = 0
            ORDER BY farm_id
            """)
    List<Long> activeFarmIds();

    /** 养殖场批次（含已结束），按入栏日期与批次 ID 排序 */
    @Select("""
            SELECT id AS batch_id, batch_no, breed_id, category_id, entry_date, entry_age_days, entry_count,
                   entry_avg_weight, current_count, status
            FROM t_batch
            WHERE farm_id = #{farmId} AND deleted = 0
              AND (#{includeEnded} = 1 OR status = 'ACTIVE')
            ORDER BY entry_date, id
            """)
    List<FeedBatchRow> farmBatches(@Param("farmId") Long farmId, @Param("includeEnded") int includeEnded);

    /** 批次入栏均重等基础信息 */
    @Select("""
            SELECT id AS batch_id, entry_date, entry_age_days, entry_count, entry_avg_weight, current_count
            FROM t_batch
            WHERE id = #{batchId} AND deleted = 0
            LIMIT 1
            """)
    FeedBatchRow batchBasics(@Param("batchId") Long batchId);
}
