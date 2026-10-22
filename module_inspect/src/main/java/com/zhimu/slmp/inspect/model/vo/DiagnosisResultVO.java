package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * AI 诊断结果（嵌入 API-I19 DiagnosisDetailVO.diagnosis）：urgency 为 NORMAL 一般 / ATTENTION 关注 / URGENT 紧急；
 * urgency_basis 为 MAJOR_EPIDEMIC / SICK_RATE / REPEAT_REPORT / AI_JUDGE；causes 1–3 个（likelihood 为 HIGH / MEDIUM / LOW）；
 * advices 1–6 条；disclaimer 固定为“仅供参考，需执业兽医确认”。
 * 测试直连时 source_label 为“测试直连 · 通义千问 qwen3.8-max”（客户端字段，服务端不返回）。
 */
public class DiagnosisResultVO {

    public static final String URGENCY_NORMAL = "NORMAL";
    public static final String URGENCY_ATTENTION = "ATTENTION";
    public static final String URGENCY_URGENT = "URGENT";
    public static final String BASIS_MAJOR_EPIDEMIC = "MAJOR_EPIDEMIC";
    public static final String BASIS_SICK_RATE = "SICK_RATE";
    public static final String BASIS_REPEAT_REPORT = "REPEAT_REPORT";
    public static final String BASIS_AI_JUDGE = "AI_JUDGE";
    public static final String LIKELIHOOD_HIGH = "HIGH";
    public static final String LIKELIHOOD_MEDIUM = "MEDIUM";
    public static final String LIKELIHOOD_LOW = "LOW";
    public static final String DISCLAIMER = "仅供参考，需执业兽医确认";

    private long diagnosisId;
    private String generatedAt;
    private String conclusion;
    private String disclaimer;
    private String urgency;
    private String urgencyText;
    private String urgencyBasis;
    private boolean isMajorEpidemic;
    private boolean suggestVet;
    private String emergencyNotice;
    private List<DiagnosisCause> causes;
    private List<String> advices;
    /** 客户端字段：测试直连的来源说明 */
    private String sourceLabel;

    public long getDiagnosisId() { return diagnosisId; }

    public String getGeneratedAt() { return generatedAt; }

    public String getConclusion() { return conclusion; }

    /** 固定声明，始终显示 */
    @NonNull
    public String getDisclaimer() { return disclaimer == null || disclaimer.isEmpty() ? DISCLAIMER : disclaimer; }

    public String getUrgency() { return urgency; }

    public String getUrgencyText() { return urgencyText; }

    public String getUrgencyBasis() { return urgencyBasis; }

    public boolean isMajorEpidemic() { return isMajorEpidemic; }

    public boolean isSuggestVet() { return suggestVet; }

    @Nullable
    public String getEmergencyNotice() { return emergencyNotice; }

    @NonNull
    public List<DiagnosisCause> getCauses() { return causes == null ? Collections.emptyList() : causes; }

    @NonNull
    public List<String> getAdvices() { return advices == null ? Collections.emptyList() : advices; }

    @Nullable
    public String getSourceLabel() { return sourceLabel; }

    public void setSourceLabel(@Nullable String sourceLabel) { this.sourceLabel = sourceLabel; }

    /** 可能性最高的病因名称 */
    @Nullable
    public String topCauseName() { return getCauses().isEmpty() ? null : getCauses().get(0).getDiseaseName(); }

    public static class DiagnosisCause {
        private int rankNo;
        private String diseaseName;
        private String likelihood;
        private String likelihoodText;
        private boolean isMajorEpidemic;
        private Long knowledgeId;

        public int getRankNo() { return rankNo; }

        public String getDiseaseName() { return diseaseName; }

        public String getLikelihood() { return likelihood; }

        public String getLikelihoodText() { return likelihoodText; }

        public boolean isMajorEpidemic() { return isMajorEpidemic; }

        @Nullable
        public Long getKnowledgeId() { return knowledgeId; }
    }
}
