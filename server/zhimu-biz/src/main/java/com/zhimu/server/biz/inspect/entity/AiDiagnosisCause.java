package com.zhimu.server.biz.inspect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** AI 诊断可能病因（t_ai_diagnosis_cause）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_ai_diagnosis_cause")
public class AiDiagnosisCause extends BaseEntity {

    private Long farmId;
    private Long diagnosisId;
    private Long reportId;
    private Integer rankNo;
    private String diseaseName;
    private Long knowledgeId;
    private BigDecimal confidence;
    /** LIKELIHOOD：HIGH / MEDIUM / LOW */
    private String likelihood;
    private Boolean isMajorEpidemic;
    private String reason;
}
