package com.zhimu.server.biz.inspect.service.impl;

import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.entity.AiDiagnosis;
import com.zhimu.server.biz.inspect.entity.AiDiagnosisAdvice;
import com.zhimu.server.biz.inspect.entity.AiDiagnosisCause;
import com.zhimu.server.biz.inspect.mapper.AbnormalReportMapper;
import com.zhimu.server.biz.inspect.mapper.AiDiagnosisAdviceMapper;
import com.zhimu.server.biz.inspect.mapper.AiDiagnosisCauseMapper;
import com.zhimu.server.biz.inspect.mapper.AiDiagnosisMapper;
import com.zhimu.server.biz.inspect.support.AbnormalWarningTodos;
import com.zhimu.server.biz.inspect.support.ParsedDiagnosis;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 诊断成功/失败的事务写库（与 AI 调用分开，避免长事务）。 */
@Service
@RequiredArgsConstructor
public class DiagnosisWriter {

    private final AiDiagnosisMapper diagnosisMapper;
    private final AiDiagnosisCauseMapper causeMapper;
    private final AiDiagnosisAdviceMapper adviceMapper;
    private final AbnormalReportMapper reportMapper;
    private final AbnormalWarningTodos warningTodos;

    /** 写入成功结果；状态已不是 RUNNING（重复回调）时返回 null，否则返回更新后的上报 */
    @Transactional
    public AbnormalReport success(Long diagnosisId, String model, long latencyMs, ParsedDiagnosis parsed,
                                  String rawOutput, String notifiedNames) {
        AiDiagnosis diagnosis = diagnosisMapper.selectById(diagnosisId);
        if (diagnosis == null || !"RUNNING".equals(diagnosis.getStatus())) {
            return null;
        }
        diagnosis.setStatus("SUCCESS");
        diagnosis.setFinishedAt(BeijingTime.now());
        diagnosis.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
        diagnosis.setModelName(model);
        diagnosis.setAiUrgency(parsed.urgency());
        diagnosis.setUrgency(parsed.urgency());
        diagnosis.setUrgencyBasis(parsed.urgencyBasis());
        diagnosis.setConclusion(parsed.conclusion());
        diagnosis.setIsMajorEpidemic(parsed.majorEpidemic());
        diagnosis.setSuggestVet(parsed.suggestVet());
        diagnosis.setOutputSnapshot(rawOutput);
        diagnosisMapper.updateById(diagnosis);

        for (ParsedDiagnosis.Cause cause : parsed.causes()) {
            AiDiagnosisCause entity = new AiDiagnosisCause();
            entity.setFarmId(diagnosis.getFarmId());
            entity.setDiagnosisId(diagnosis.getId());
            entity.setReportId(diagnosis.getReportId());
            entity.setRankNo(cause.rankNo());
            entity.setDiseaseName(cause.diseaseName());
            entity.setKnowledgeId(cause.knowledgeId());
            entity.setConfidence(cause.confidence());
            entity.setLikelihood(cause.likelihood());
            entity.setIsMajorEpidemic(cause.majorEpidemic());
            entity.setReason(cause.reason());
            causeMapper.insert(entity);
        }
        int seq = 1;
        for (String advice : parsed.advices()) {
            AiDiagnosisAdvice entity = new AiDiagnosisAdvice();
            entity.setFarmId(diagnosis.getFarmId());
            entity.setDiagnosisId(diagnosis.getId());
            entity.setSeqNo(seq++);
            entity.setAdviceType(adviceType(advice));
            entity.setContent(advice);
            adviceMapper.insert(entity);
        }

        AbnormalReport report = reportMapper.selectById(diagnosis.getReportId());
        if (report == null) {
            return null;
        }
        report.setStatus("PENDING".equals(report.getStatus()) ? "DIAGNOSED" : report.getStatus());
        report.setAiState("DONE");
        report.setCurrentDiagnosisId(diagnosis.getId());
        report.setUrgency(parsed.urgency());
        report.setIsMajorEpidemic(parsed.majorEpidemic());
        report.setTopCauseName(parsed.causes().isEmpty() ? null : parsed.causes().get(0).diseaseName());
        report.setNotifiedNames(notifiedNames);
        report.setDiagnosedAt(diagnosis.getFinishedAt());
        report.setUnavailableSince(null);
        if (!"SUBMIT".equals(diagnosis.getTriggerType())) {
            report.setAutoRetryUsed(true);
        }
        reportMapper.updateById(report);
        // 诊断完成：预警待办改为“AI 诊断待处理”并带上疑似病因与紧急程度
        warningTodos.sync(report);
        return report;
    }

    /** 写入失败结果并记下已通知的人；状态已不是 RUNNING 时返回 null */
    @Transactional
    public AbnormalReport failure(Long diagnosisId, String failReason, long latencyMs, String rawOutput,
                                  String notifiedNames) {
        AiDiagnosis diagnosis = diagnosisMapper.selectById(diagnosisId);
        if (diagnosis == null || !"RUNNING".equals(diagnosis.getStatus())) {
            return null;
        }
        diagnosis.setStatus("TIMEOUT".equals(failReason) ? "TIMEOUT" : "FAILED");
        diagnosis.setFailReason(failReason);
        diagnosis.setFinishedAt(BeijingTime.now());
        diagnosis.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
        diagnosis.setOutputSnapshot(rawOutput);
        diagnosisMapper.updateById(diagnosis);

        AbnormalReport report = reportMapper.selectById(diagnosis.getReportId());
        if (report == null) {
            return null;
        }
        if ("MANUAL_RETRY".equals(diagnosis.getTriggerType())) {
            report.setAutoRetryUsed(true);
        }
        if (Boolean.TRUE.equals(report.getAutoRetryUsed())) {
            report.setAiState("FAILED");
        } else {
            report.setAiState("UNAVAILABLE");
            report.setUnavailableSince(BeijingTime.now());
        }
        if (notifiedNames != null) {
            report.setNotifiedNames(notifiedNames);
        }
        reportMapper.updateById(report);
        return report;
    }

    private static String adviceType(String advice) {
        if (advice.contains("隔离") || advice.contains("封锁")) {
            return "ISOLATE";
        }
        if (advice.contains("消毒")) {
            return "DISINFECT";
        }
        if (advice.contains("药")) {
            return "MEDICATE";
        }
        if (advice.contains("送检") || advice.contains("检测")) {
            return "LAB_TEST";
        }
        return "OTHER";
    }
}
