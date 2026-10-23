package com.zhimu.server.biz.inspect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.inspect.entity.AiDiagnosis;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** AI 诊断 Mapper。 */
public interface AiDiagnosisMapper extends BaseMapper<AiDiagnosis> {

    @Select("SELECT * FROM t_ai_diagnosis WHERE report_id = #{reportId} ORDER BY attempt_no DESC, id DESC LIMIT 1")
    AiDiagnosis selectLatestByReportId(@Param("reportId") Long reportId);

    @Select("SELECT * FROM t_ai_diagnosis WHERE report_id = #{reportId} AND status = 'SUCCESS' "
            + "ORDER BY attempt_no DESC, id DESC LIMIT 1")
    AiDiagnosis selectSuccessByReportId(@Param("reportId") Long reportId);

    @Select("SELECT COALESCE(MAX(attempt_no), 0) FROM t_ai_diagnosis WHERE report_id = #{reportId}")
    int maxAttemptNo(@Param("reportId") Long reportId);

    @Select("SELECT COUNT(1) FROM t_ai_diagnosis WHERE report_id = #{reportId} AND status = 'RUNNING'")
    int countRunning(@Param("reportId") Long reportId);
}
