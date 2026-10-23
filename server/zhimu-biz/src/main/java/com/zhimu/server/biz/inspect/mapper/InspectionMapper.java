package com.zhimu.server.biz.inspect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.inspect.entity.Inspection;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/** 巡检记录 Mapper（自定义 SQL 显式过滤 deleted，不依赖逻辑删除插件）。 */
public interface InspectionMapper extends BaseMapper<Inspection> {

    /** 修改申请按 ID 读取原记录：已删除的记录也要返回，由公共能力给出 41001 */
    @Select("SELECT * FROM t_inspection WHERE id = #{id}")
    Inspection selectByIdIncludingDeleted(@Param("id") Long id);

    @Select("SELECT * FROM t_inspection WHERE farm_id = #{farmId} AND pen_id = #{penId} "
            + "AND inspect_date = #{date} AND deleted = 0 LIMIT 1")
    Inspection selectByPenDate(@Param("farmId") Long farmId, @Param("penId") Long penId,
                               @Param("date") LocalDate date);

    @Select("SELECT * FROM t_inspection WHERE farm_id = #{farmId} AND client_request_id = #{clientRequestId} "
            + "AND deleted = 0 LIMIT 1")
    Inspection selectByClientRequestId(@Param("farmId") Long farmId,
                                       @Param("clientRequestId") String clientRequestId);

    @Select("SELECT * FROM t_inspection WHERE farm_id = #{farmId} AND inspect_date = #{date} AND deleted = 0")
    List<Inspection> selectDay(@Param("farmId") Long farmId, @Param("date") LocalDate date);

    @Select("SELECT * FROM t_inspection WHERE farm_id = #{farmId} AND pen_id = #{penId} "
            + "AND inspect_date BETWEEN #{from} AND #{to} AND deleted = 0 "
            + "ORDER BY inspect_date DESC")
    List<Inspection> selectRange(@Param("farmId") Long farmId, @Param("penId") Long penId,
                                 @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT * FROM t_inspection WHERE farm_id = #{farmId} AND inspect_status IN ('DONE','MISSED') "
            + "AND inspect_date BETWEEN #{from} AND #{to} AND deleted = 0 ORDER BY inspect_date DESC, id DESC")
    List<Inspection> selectIssues(@Param("farmId") Long farmId, @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    @Select("SELECT * FROM t_inspection WHERE farm_id = #{farmId} AND report_id = #{reportId} "
            + "AND deleted = 0 LIMIT 1")
    Inspection selectByReportId(@Param("farmId") Long farmId, @Param("reportId") Long reportId);
}
