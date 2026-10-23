package com.zhimu.server.biz.inspect.support;

/** 模型输出与业务兜底后的诊断结果。 */
public record ParsedDiagnosis(String conclusion, String urgency, String urgencyBasis, boolean majorEpidemic,
                              boolean suggestVet, String emergencyNotice, java.util.List<Cause> causes,
                              java.util.List<String> advices) {

    public record Cause(int rankNo, String diseaseName, String likelihood, java.math.BigDecimal confidence,
                        boolean majorEpidemic, Long knowledgeId, String reason) {
    }
}
