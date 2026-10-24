package com.zhimu.server.biz.inspect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.query.LinkableRow;
import com.zhimu.server.biz.inspect.query.ReportListRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 异常上报 Mapper。 */
public interface AbnormalReportMapper extends BaseMapper<AbnormalReport> {

    @Select("SELECT * FROM t_abnormal_report WHERE farm_id = #{farmId} AND client_request_id = #{clientRequestId} "
            + "AND deleted = 0 LIMIT 1")
    AbnormalReport selectByClientRequestId(@Param("farmId") Long farmId,
                                           @Param("clientRequestId") String clientRequestId);

    @Select("SELECT * FROM t_abnormal_report WHERE farm_id = #{farmId} AND inspection_id = #{inspectionId} "
            + "AND deleted = 0 LIMIT 1")
    AbnormalReport selectByInspectionId(@Param("farmId") Long farmId, @Param("inspectionId") Long inspectionId);

    @Select("SELECT COUNT(1) FROM t_abnormal_report WHERE farm_id = #{farmId} AND batch_id = #{batchId} "
            + "AND report_date = #{date} AND deleted = 0")
    int countSameDay(@Param("farmId") Long farmId, @Param("batchId") Long batchId,
                     @Param("date") LocalDate date);

    @Select("SELECT r.id AS reportId, r.current_diagnosis_id AS diagnosisId, r.reported_at AS reportedAt, "
            + "r.pen_id AS penId, p.pen_name AS penName, r.batch_id AS batchId, b.batch_no AS batchNo, "
            + "r.top_cause_name AS topCauseName, r.status AS status "
            + "FROM t_abnormal_report r JOIN t_pen p ON p.id = r.pen_id JOIN t_batch b ON b.id = r.batch_id "
            + "WHERE r.farm_id = #{farmId} AND r.deleted = 0 AND r.reported_at >= #{from} "
            + "AND (#{batchId} IS NULL OR r.batch_id = #{batchId}) "
            + "AND (#{penId} IS NULL OR r.pen_id = #{penId}) "
            + "ORDER BY r.reported_at DESC, r.id DESC LIMIT 100")
    List<LinkableRow> selectLinkable(@Param("farmId") Long farmId, @Param("batchId") Long batchId,
                                     @Param("penId") Long penId, @Param("from") LocalDateTime from);

    /** API-I27 异常上报分页列表（联表栏舍/批次/上报人）。 */
    @Select("<script>"
            + "SELECT r.id AS report_id, r.version, r.reported_at, r.report_date, r.pen_id, p.pen_name, "
            + "r.batch_id, b.batch_no, r.sick_count, r.stock_snapshot, r.sick_rate, r.description, "
            + "r.status, r.ai_state, r.urgency, r.is_major_epidemic, r.top_cause_name, r.reporter_id, "
            + "u.name AS reporter_name, r.inspection_id, r.source, "
            + "(SELECT GROUP_CONCAT(s.symptom_name ORDER BY s.sort_no SEPARATOR '、') "
            + " FROM t_abnormal_report_symptom s WHERE s.report_id = r.id) AS symptom_names "
            + "FROM t_abnormal_report r "
            + "JOIN t_pen p ON p.id = r.pen_id "
            + "JOIN t_batch b ON b.id = r.batch_id AND b.deleted = 0 "
            + "LEFT JOIN t_user u ON u.id = r.reporter_id "
            + "WHERE r.farm_id = #{farmId} AND r.deleted = 0 "
            + "AND r.reported_at &gt;= #{from} AND r.reported_at &lt; #{toExclusive} "
            + "<if test='staffUserId != null'> AND r.reporter_id = #{staffUserId}</if>"
            + "<if test='status != null'> AND r.status = #{status}</if>"
            + "<if test='penId != null'> AND r.pen_id = #{penId}</if>"
            + "<if test='batchId != null'> AND r.batch_id = #{batchId}</if>"
            + "<if test='urgency != null'> AND r.urgency = #{urgency}</if>"
            + "<if test='keyword != null'> AND (p.pen_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR b.batch_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR r.top_cause_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR r.description LIKE CONCAT('%', #{keyword}, '%') "
            + "OR u.name LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "ORDER BY r.reported_at DESC, r.id DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<ReportListRow> selectReportPage(@Param("farmId") Long farmId,
                                         @Param("staffUserId") Long staffUserId,
                                         @Param("status") String status,
                                         @Param("penId") Long penId,
                                         @Param("batchId") Long batchId,
                                         @Param("urgency") String urgency,
                                         @Param("keyword") String keyword,
                                         @Param("from") LocalDateTime from,
                                         @Param("toExclusive") LocalDateTime toExclusive,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    /** API-I27 列表总数。 */
    @Select("<script>"
            + "SELECT COUNT(*) FROM t_abnormal_report r "
            + "JOIN t_pen p ON p.id = r.pen_id "
            + "JOIN t_batch b ON b.id = r.batch_id AND b.deleted = 0 "
            + "LEFT JOIN t_user u ON u.id = r.reporter_id "
            + "WHERE r.farm_id = #{farmId} AND r.deleted = 0 "
            + "AND r.reported_at &gt;= #{from} AND r.reported_at &lt; #{toExclusive} "
            + "<if test='staffUserId != null'> AND r.reporter_id = #{staffUserId}</if>"
            + "<if test='status != null'> AND r.status = #{status}</if>"
            + "<if test='penId != null'> AND r.pen_id = #{penId}</if>"
            + "<if test='batchId != null'> AND r.batch_id = #{batchId}</if>"
            + "<if test='urgency != null'> AND r.urgency = #{urgency}</if>"
            + "<if test='keyword != null'> AND (p.pen_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR b.batch_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR r.top_cause_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR r.description LIKE CONCAT('%', #{keyword}, '%') "
            + "OR u.name LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "</script>")
    long countReports(@Param("farmId") Long farmId,
                      @Param("staffUserId") Long staffUserId,
                      @Param("status") String status,
                      @Param("penId") Long penId,
                      @Param("batchId") Long batchId,
                      @Param("urgency") String urgency,
                      @Param("keyword") String keyword,
                      @Param("from") LocalDateTime from,
                      @Param("toExclusive") LocalDateTime toExclusive);

    /** API-I27 状态分段计数（不应用 status 条件，其余筛选条件一致）。 */
    @Select("<script>"
            + "SELECT r.status AS status, COUNT(*) AS cnt FROM t_abnormal_report r "
            + "JOIN t_pen p ON p.id = r.pen_id "
            + "JOIN t_batch b ON b.id = r.batch_id AND b.deleted = 0 "
            + "LEFT JOIN t_user u ON u.id = r.reporter_id "
            + "WHERE r.farm_id = #{farmId} AND r.deleted = 0 "
            + "AND r.reported_at &gt;= #{from} AND r.reported_at &lt; #{toExclusive} "
            + "<if test='staffUserId != null'> AND r.reporter_id = #{staffUserId}</if>"
            + "<if test='penId != null'> AND r.pen_id = #{penId}</if>"
            + "<if test='batchId != null'> AND r.batch_id = #{batchId}</if>"
            + "<if test='urgency != null'> AND r.urgency = #{urgency}</if>"
            + "<if test='keyword != null'> AND (p.pen_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR b.batch_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR r.top_cause_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR r.description LIKE CONCAT('%', #{keyword}, '%') "
            + "OR u.name LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "GROUP BY r.status"
            + "</script>")
    List<Map<String, Object>> countByStatus(@Param("farmId") Long farmId,
                                            @Param("staffUserId") Long staffUserId,
                                            @Param("penId") Long penId,
                                            @Param("batchId") Long batchId,
                                            @Param("urgency") String urgency,
                                            @Param("keyword") String keyword,
                                            @Param("from") LocalDateTime from,
                                            @Param("toExclusive") LocalDateTime toExclusive);
}
