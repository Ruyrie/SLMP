package com.zhimu.server.biz.inspect.convert;

import com.zhimu.server.biz.inspect.entity.AiDiagnosis;
import com.zhimu.server.biz.inspect.entity.AiDiagnosisAdvice;
import com.zhimu.server.biz.inspect.entity.AiDiagnosisCause;
import com.zhimu.server.biz.inspect.entity.DiagnosisHandle;
import com.zhimu.server.biz.inspect.vo.DiagnosisResultVO;
import com.zhimu.server.biz.inspect.vo.DiagnosisStateVO;
import com.zhimu.server.biz.inspect.vo.HandleVO;
import com.zhimu.server.common.util.BeijingTime;

import java.util.ArrayList;
import java.util.List;

/** AI 诊断结果、处理结果到 App VO 的转换。 */
public final class DiagnosisConvert {

    private DiagnosisConvert() {
    }

    public static String urgencyText(String urgency) {
        if (urgency == null) {
            return null;
        }
        return switch (urgency) {
            case "URGENT" -> "紧急";
            case "ATTENTION" -> "关注";
            default -> "一般";
        };
    }

    public static String likelihoodText(String likelihood) {
        if (likelihood == null) {
            return null;
        }
        return switch (likelihood) {
            case "HIGH" -> "较高";
            case "LOW" -> "较低";
            default -> "中等";
        };
    }

    public static String aiMatchText(String match) {
        if (match == null) {
            return null;
        }
        return switch (match) {
            case "MATCH" -> "相符";
            case "NOT_MATCH" -> "不相符";
            default -> "无诊断结果";
        };
    }

    public static String reportStatusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "DIAGNOSED" -> "已诊断";
            case "HANDLED" -> "已处理";
            case "CLOSED" -> "已结案";
            default -> "待诊断";
        };
    }

    public static String aiStateText(String aiState) {
        if (aiState == null) {
            return null;
        }
        return switch (aiState) {
            case "DONE" -> "已完成";
            case "UNAVAILABLE" -> "暂不可用";
            case "FAILED" -> "诊断失败";
            default -> "诊断中";
        };
    }

    public static DiagnosisResultVO result(AiDiagnosis diagnosis, List<AiDiagnosisCause> causes,
                                           List<AiDiagnosisAdvice> advices) {
        if (diagnosis == null) {
            return null;
        }
        DiagnosisResultVO vo = new DiagnosisResultVO();
        vo.setDiagnosisId(diagnosis.getId());
        vo.setGeneratedAt(BeijingTime.format(diagnosis.getFinishedAt()));
        vo.setConclusion(diagnosis.getConclusion());
        vo.setDisclaimer("仅供参考，需执业兽医确认");
        vo.setUrgency(diagnosis.getUrgency());
        vo.setUrgencyText(urgencyText(diagnosis.getUrgency()));
        vo.setUrgencyBasis(diagnosis.getUrgencyBasis());
        vo.setIsMajorEpidemic(diagnosis.getIsMajorEpidemic());
        vo.setSuggestVet(Boolean.TRUE.equals(diagnosis.getSuggestVet()));
        if (Boolean.TRUE.equals(diagnosis.getIsMajorEpidemic())) {
            vo.setEmergencyNotice("疑似重大动物疫病。请联系执业兽医并向当地动物防疫机构报告。");
        }
        List<DiagnosisResultVO.DiagnosisCause> causeVos = new ArrayList<>();
        if (causes != null) {
            for (AiDiagnosisCause cause : causes) {
                DiagnosisResultVO.DiagnosisCause item = new DiagnosisResultVO.DiagnosisCause();
                item.setRankNo(cause.getRankNo() == null ? 0 : cause.getRankNo());
                item.setDiseaseName(cause.getDiseaseName());
                item.setLikelihood(cause.getLikelihood());
                item.setLikelihoodText(likelihoodText(cause.getLikelihood()));
                item.setIsMajorEpidemic(cause.getIsMajorEpidemic());
                item.setKnowledgeId(cause.getKnowledgeId());
                causeVos.add(item);
            }
        }
        vo.setCauses(causeVos);
        List<String> adviceTexts = new ArrayList<>();
        if (advices != null) {
            for (AiDiagnosisAdvice advice : advices) {
                adviceTexts.add(advice.getContent());
            }
        }
        vo.setAdvices(adviceTexts);
        return vo;
    }

    public static HandleVO handle(DiagnosisHandle handle, String handledByName, String updatedByName,
                                  boolean medicationConverted, boolean closed) {
        if (handle == null) {
            return null;
        }
        HandleVO vo = new HandleVO();
        vo.setHandleId(handle.getId());
        vo.setDiagnostician(handle.getDiagnostician());
        vo.setTreatment(handle.getTreatment());
        vo.setUsedDrug(Boolean.TRUE.equals(handle.getUsedDrug()));
        vo.setMedicationConverted(medicationConverted);
        int cured = handle.getCuredCount() == null ? 0 : handle.getCuredCount();
        int culled = handle.getCulledCount() == null ? 0 : handle.getCulledCount();
        vo.setCuredCount(cured);
        vo.setCulledCount(culled);
        vo.setTotalCount(cured + culled);
        vo.setAiMatch(handle.getAiMatch());
        vo.setAiMatchText(aiMatchText(handle.getAiMatch()));
        vo.setHandledByName(handledByName);
        vo.setHandledAt(BeijingTime.format(handle.getHandledAt()));
        vo.setUpdatedByName(updatedByName);
        vo.setUpdatedAt(BeijingTime.format(handle.getUpdatedAt()));
        vo.setHasRevisions(false);
        vo.setClosed(closed);
        vo.setVersion(handle.getVersion() == null ? 0 : handle.getVersion());
        return vo;
    }

    public static DiagnosisStateVO state(com.zhimu.server.biz.inspect.entity.AbnormalReport report,
                                         String lastFailureText) {
        DiagnosisStateVO vo = new DiagnosisStateVO();
        vo.setReportId(report.getId());
        vo.setStatus(report.getStatus());
        vo.setAiState(report.getAiState());
        vo.setDiagnosedAt(BeijingTime.format(report.getDiagnosedAt()));
        vo.setUrgency(report.getUrgency());
        vo.setLastFailureText(lastFailureText);
        vo.setAutoRetryPending("PENDING".equals(report.getStatus())
                && "UNAVAILABLE".equals(report.getAiState())
                && !Boolean.TRUE.equals(report.getAutoRetryUsed()));
        return vo;
    }
}
