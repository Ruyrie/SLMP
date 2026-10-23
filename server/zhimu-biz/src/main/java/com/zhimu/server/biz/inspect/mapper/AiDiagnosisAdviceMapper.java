package com.zhimu.server.biz.inspect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.inspect.entity.AiDiagnosisAdvice;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** AI 诊断建议 Mapper。 */
public interface AiDiagnosisAdviceMapper extends BaseMapper<AiDiagnosisAdvice> {

    @Select("SELECT * FROM t_ai_diagnosis_advice WHERE diagnosis_id = #{diagnosisId} ORDER BY seq_no")
    List<AiDiagnosisAdvice> selectByDiagnosisId(@Param("diagnosisId") Long diagnosisId);
}
