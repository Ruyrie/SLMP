package com.zhimu.server.biz.inspect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

/** AI 诊断处理建议（t_ai_diagnosis_advice）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_ai_diagnosis_advice")
public class AiDiagnosisAdvice extends BaseEntity {

    private Long farmId;
    private Long diagnosisId;
    private Integer seqNo;
    /** ADVICE_TYPE：ISOLATE / MEDICATE / DISINFECT / LAB_TEST / OTHER */
    private String adviceType;
    private String content;
}
