package com.zhimu.server.biz.inspect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.inspect.entity.AbnormalReportSymptom;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 上报症状 Mapper。 */
public interface AbnormalReportSymptomMapper extends BaseMapper<AbnormalReportSymptom> {

    @Select("SELECT * FROM t_abnormal_report_symptom WHERE report_id = #{reportId} ORDER BY sort_no, id")
    List<AbnormalReportSymptom> selectByReportId(@Param("reportId") Long reportId);
}
