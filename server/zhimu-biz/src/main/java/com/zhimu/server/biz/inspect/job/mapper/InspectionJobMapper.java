package com.zhimu.server.biz.inspect.job.mapper;

import com.zhimu.server.biz.inspect.job.InspectionTodoRow;
import com.zhimu.server.biz.inspect.job.StockedPenRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** 巡检任务每日生成用的查询（定时任务与启动补齐，没有养殖场上下文时调用）。 */
public interface InspectionJobMapper {

    /** 日常任务只为正常营业、审核通过的养殖场生成（与饲喂、健康任务口径一致） */
    @Select("SELECT id FROM t_farm WHERE deleted = 0 AND disabled = 0 AND frozen = 0 "
            + "AND audit_status = 'APPROVED' ORDER BY id")
    List<Long> selectActiveFarmIds();

    /** 在养批次有在栏数的启用栏舍（含批次入栏日期），按栏舍、批次排序 */
    @Select("SELECT bp.pen_id AS penId, p.pen_name AS penName, bp.batch_id AS batchId, b.batch_no AS batchNo, "
            + "b.entry_date AS entryDate, bp.stock_count AS stockCount "
            + "FROM t_batch_pen bp JOIN t_batch b ON b.id = bp.batch_id JOIN t_pen p ON p.id = bp.pen_id "
            + "WHERE bp.farm_id = #{farmId} AND b.farm_id = #{farmId} AND b.status = 'ACTIVE' AND b.deleted = 0 "
            + "AND bp.stock_count > 0 AND p.status = 'ENABLED' ORDER BY bp.pen_id, bp.batch_id")
    List<StockedPenRow> selectStockedPens(@Param("farmId") Long farmId);

    @Select("SELECT deadline_time FROM t_inspection_setting WHERE farm_id = #{farmId} LIMIT 1")
    LocalTime selectDeadline(@Param("farmId") Long farmId);

    /** 今天以前最近一个有巡检行的日期（判断停机漏生成的天数） */
    @Select("SELECT MAX(inspect_date) FROM t_inspection WHERE farm_id = #{farmId} AND deleted = 0 "
            + "AND inspect_date < #{before}")
    LocalDate selectLastInspectDate(@Param("farmId") Long farmId, @Param("before") LocalDate before);

    /** 今天以前仍未登记的占位行标记为漏巡（登记过的行不动） */
    @Update("UPDATE t_inspection SET inspect_status = 'MISSED', updated_at = #{now} "
            + "WHERE farm_id = #{farmId} AND inspect_status = 'PENDING' AND registrant_id IS NULL "
            + "AND deleted = 0 AND inspect_date < #{today}")
    int markMissed(@Param("farmId") Long farmId, @Param("today") LocalDate today, @Param("now") LocalDateTime now);

    /** 某天的巡检待办（含示例数据中去重键不是巡检记录 ID 的行） */
    @Select("SELECT id, pen_id AS penId, dedup_key AS dedupKey, status, due_date AS dueDate FROM t_todo "
            + "WHERE farm_id = #{farmId} AND todo_type = 'INSPECTION' AND deleted = 0 AND due_date = #{date}")
    List<InspectionTodoRow> selectTodosOn(@Param("farmId") Long farmId, @Param("date") LocalDate date);

    /** 截止日期早于 before 仍待处理的巡检待办 */
    @Select("SELECT id, pen_id AS penId, dedup_key AS dedupKey, status, due_date AS dueDate FROM t_todo "
            + "WHERE farm_id = #{farmId} AND todo_type = 'INSPECTION' AND deleted = 0 AND status = 'PENDING' "
            + "AND due_date < #{before}")
    List<InspectionTodoRow> selectPendingTodosBefore(@Param("farmId") Long farmId,
                                                     @Param("before") LocalDate before);
}
