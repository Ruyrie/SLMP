package com.zhimu.server.biz.inspect.vo;

import lombok.Data;

import java.util.List;

/** AI 诊断结果（嵌入 DiagnosisDetailVO）。urgency：NORMAL / ATTENTION / URGENT。 */
@Data
public class DiagnosisResultVO {

    private long diagnosisId;
    private String generatedAt;
    private String conclusion;
    private String disclaimer;
    private String urgency;
    private String urgencyText;
    private String urgencyBasis;
    private Boolean isMajorEpidemic;
    private boolean suggestVet;
    private String emergencyNotice;
    private List<DiagnosisCause> causes;
    private List<String> advices;

    @Data
    public static class DiagnosisCause {
        private int rankNo;
        private String diseaseName;
        private String likelihood;
        private String likelihoodText;
        private Boolean isMajorEpidemic;
        private Long knowledgeId;
    }
}
