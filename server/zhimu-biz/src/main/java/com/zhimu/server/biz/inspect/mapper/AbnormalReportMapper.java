package com.zhimu.server.biz.inspect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.query.LinkableRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
}
