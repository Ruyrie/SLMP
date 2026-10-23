package com.zhimu.server.biz.inspect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.inspect.entity.AiDiagnosisCause;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** AI 诊断病因 Mapper。 */
public interface AiDiagnosisCauseMapper extends BaseMapper<AiDiagnosisCause> {

    @Select("SELECT * FROM t_ai_diagnosis_cause WHERE diagnosis_id = #{diagnosisId} ORDER BY rank_no")
    List<AiDiagnosisCause> selectByDiagnosisId(@Param("diagnosisId") Long diagnosisId);
}
