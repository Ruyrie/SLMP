package com.zhimu.server.biz.inspect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.inspect.entity.DiagnosisHandle;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 诊断处理结果 Mapper。 */
public interface DiagnosisHandleMapper extends BaseMapper<DiagnosisHandle> {

    @Select("SELECT * FROM t_diagnosis_handle WHERE report_id = #{reportId} LIMIT 1")
    DiagnosisHandle selectByReportId(@Param("reportId") Long reportId);
}
