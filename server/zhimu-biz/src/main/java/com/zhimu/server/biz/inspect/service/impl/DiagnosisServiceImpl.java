package com.zhimu.server.biz.inspect.service.impl;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.entity.AbnormalReportSymptom;
import com.zhimu.server.biz.inspect.entity.AiDiagnosis;
import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.entity.InspectionItemResult;
import com.zhimu.server.biz.inspect.mapper.AbnormalReportMapper;
import com.zhimu.server.biz.inspect.mapper.AbnormalReportSymptomMapper;
import com.zhimu.server.biz.inspect.mapper.AiDiagnosisMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionItemResultMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionMapper;
import com.zhimu.server.biz.inspect.service.DiagnosisService;
import com.zhimu.server.biz.inspect.support.DiagnosisInput;
import com.zhimu.server.biz.inspect.support.DiagnosisParser;
import com.zhimu.server.biz.inspect.support.DiagnosisPromptBuilder;
import com.zhimu.server.biz.inspect.support.InspectSupport;
import com.zhimu.server.biz.inspect.support.ParsedDiagnosis;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.framework.async.AsyncConfig;
import com.zhimu.server.integration.ai.AiChatRequest;
import com.zhimu.server.integration.ai.AiChatResult;
import com.zhimu.server.integration.ai.AiGateway;
import com.zhimu.server.integration.ai.AiMessage;
import com.zhimu.server.integration.ai.AiScene;
import com.zhimu.server.integration.ai.BannedDrugGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** AI 诊断执行（查询、提示词、调用网关、结果写库）。 */
@Service
@RequiredArgsConstructor
public class DiagnosisServiceImpl implements DiagnosisService {

    private final AiGateway aiGateway;
    private final AbnormalReportMapper reportMapper;
    private final AbnormalReportSymptomMapper symptomMapper;
    private final AiDiagnosisMapper diagnosisMapper;
    private final InspectionMapper inspectionMapper;
    private final InspectionItemResultMapper itemResultMapper;
    private final BatchQueryService batchQueryService;
    private final BaseDataQueryService baseDataQueryService;
    private final BreedQueryService breedQueryService;
    private final BizMediaService bizMediaService;
    private final InspectSupport support;
    private final DiagnosisWriter writer;
    private final ObjectProvider<BannedDrugGuard> bannedDrugGuard;

    @Override
    public AiDiagnosis createAttempt(AbnormalReport report, String triggerType, Long requestedBy) {
        int attemptNo = diagnosisMapper.maxAttemptNo(report.getId()) + 1;
        AiDiagnosis diagnosis = new AiDiagnosis();
        diagnosis.setFarmId(report.getFarmId());
        diagnosis.setReportId(report.getId());
        diagnosis.setAttemptNo(attemptNo);
        diagnosis.setTriggerType(triggerType == null ? "SUBMIT" : triggerType);
        diagnosis.setStatus("RUNNING");
        diagnosis.setRequestedBy(requestedBy);
        diagnosis.setRequestedAt(BeijingTime.now());
        diagnosis.setPromptVersion(DiagnosisPromptBuilder.PROMPT_VERSION);
        diagnosis.setSuggestVet(true);
        diagnosis.setIsMajorEpidemic(false);
        diagnosisMapper.insert(diagnosis);

        report.setAiState("DIAGNOSING");
        report.setUpdatedBy(requestedBy);
        reportMapper.updateById(report);
        return diagnosis;
    }

    @Async(AsyncConfig.AI_EXECUTOR)
    @Override
    public void executeAsync(Long diagnosisId) {
        execute(diagnosisId);
    }

    @Override
    public void execute(Long diagnosisId) {
        if (diagnosisId == null) {
            return;
        }
        AiDiagnosis diagnosis = diagnosisMapper.selectById(diagnosisId);
        if (diagnosis == null || !"RUNNING".equals(diagnosis.getStatus())) {
            return;
        }
        AbnormalReport report = reportMapper.selectById(diagnosis.getReportId());
        if (report == null) {
            return;
        }
        long startedAt = System.currentTimeMillis();
        String raw = null;
        try {
            if (!aiGateway.isEnabled()) {
                writer.failure(diagnosisId, "SERVICE_UNAVAILABLE", 0, null);
                return;
            }
            DiagnosisInput input = buildInput(report);
            List<String> images = images(report.getId());
            String text = DiagnosisPromptBuilder.userPrompt(input);
            AiMessage user = images.isEmpty() ? AiMessage.user(text) : AiMessage.user(text, images);
            AiChatRequest request = AiChatRequest.builder()
                    .scene(AiScene.DIAGNOSIS)
                    .message(AiMessage.system(DiagnosisPromptBuilder.systemPrompt()))
                    .message(user)
                    .jsonMode(true)
                    .maxTokens(2000)
                    .build();
            AiChatResult result = aiGateway.chat(request);
            raw = result.getContent();
            ParsedDiagnosis parsed = DiagnosisParser.parse(raw, report.getSickCount(),
                    report.getSickRate(), report.getSameDaySeq() == null ? 1 : report.getSameDaySeq());
            parsed = filterBanned(parsed);
            long elapsed = System.currentTimeMillis() - startedAt;
            String names = String.join("、", writer.notificationNames(report));
            String model = result.getModel() == null ? "qwen3.8-max" : result.getModel();
            writer.success(diagnosisId, model, elapsed, parsed, raw, names);
        } catch (DiagnosisParser.InvalidDiagnosisOutputException e) {
            writer.failure(diagnosisId, "INVALID_OUTPUT", System.currentTimeMillis() - startedAt, raw);
        } catch (RuntimeException e) {
            String reason = isTimeout(e) ? "TIMEOUT" : "SERVICE_UNAVAILABLE";
            writer.failure(diagnosisId, reason, System.currentTimeMillis() - startedAt, raw);
        }
    }

    @Override
    public String lastFailureText(AbnormalReport report) {
        if (report == null || report.getId() == null || "DONE".equals(report.getAiState())) {
            return null;
        }
        AiDiagnosis latest = diagnosisMapper.selectLatestByReportId(report.getId());
        if (latest == null) {
            return null;
        }
        String reason = latest.getFailReason();
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case "TIMEOUT" -> "诊断请求超时";
            case "INVALID_OUTPUT" -> "诊断结果无效";
            case "SERVICE_UNAVAILABLE" -> "AI 服务暂不可用";
            default -> "诊断失败";
        };
    }

    private DiagnosisInput buildInput(AbnormalReport report) {
        BatchInfo batch = batchQueryService.requireBatch(report.getBatchId());
        CategoryInfo category = baseDataQueryService.getCategory(batch.categoryId());
        String breedName = null;
        if (batch.breedId() != null) {
            BreedQueryService.BreedInfo breed = breedQueryService.info(batch.breedId());
            breedName = breed == null ? null : breed.name();
        }
        Integer dayAge = null;
        if (batch.entryAgeDays() != null && batch.entryDate() != null) {
            dayAge = batch.entryAgeDays() + (int) ChronoUnit.DAYS.between(batch.entryDate(),
                    report.getReportDate() == null ? BeijingTime.today() : report.getReportDate());
        }
        List<String> symptoms = new ArrayList<>();
        List<AbnormalReportSymptom> symptomRows = symptomMapper.selectByReportId(report.getId());
        if (symptomRows != null) {
            for (AbnormalReportSymptom symptom : symptomRows) {
                symptoms.add(symptom.getSymptomName());
            }
        }
        List<String> inspectionItems = new ArrayList<>();
        String manualReason = null;
        if (report.getInspectionId() != null) {
            Inspection inspection = inspectionMapper.selectById(report.getInspectionId());
            if (inspection != null) {
                manualReason = inspection.getManualReason();
                List<InspectionItemResult> results =
                        itemResultMapper.selectByInspectionId(report.getInspectionId());
                for (InspectionItemResult result : results) {
                    inspectionItems.add(result.getItemName() + " " + result.getOptionName());
                }
            }
        }
        return new DiagnosisInput(report.getId(), support.farmName(), support.penName(report.getPenId()),
                batch.batchNo(), breedName, category == null ? null : category.name(), dayAge,
                report.getStockSnapshot(), batch.entryDate(), batch.entryAgeDays(), report.getSource(),
                symptoms, report.getSickCount() == null ? 0 : report.getSickCount(), report.getSickRate(),
                report.getDescription(), report.getSameDaySeq() == null ? 1 : report.getSameDaySeq(),
                inspectionItems, manualReason);
    }

    private List<String> images(Long reportId) {
        List<String> images = new ArrayList<>();
        for (MediaItemVO media : bizMediaService.list(BizType.ABNORMAL_REPORT, reportId)) {
            if ("IMAGE".equals(media.getMediaType()) && media.getUrl() != null && !media.getUrl().isBlank()) {
                images.add(media.getUrl());
            }
        }
        return DiagnosisPromptBuilder.limitImages(images);
    }

    private ParsedDiagnosis filterBanned(ParsedDiagnosis parsed) {
        BannedDrugGuard guard = bannedDrugGuard == null ? null : bannedDrugGuard.getIfAvailable();
        if (guard == null) {
            return parsed;
        }
        List<String> advices = new ArrayList<>();
        for (String advice : parsed.advices()) {
            if (guard.check(advice).isEmpty()) {
                advices.add(advice);
            }
        }
        if (advices.isEmpty()) {
            throw new DiagnosisParser.InvalidDiagnosisOutputException("建议全部命中禁用药");
        }
        return new ParsedDiagnosis(parsed.conclusion(), parsed.urgency(), parsed.urgencyBasis(),
                parsed.majorEpidemic(), parsed.suggestVet(), parsed.emergencyNotice(), parsed.causes(), advices);
    }

    private static boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (current.getClass().getSimpleName().toLowerCase().contains("timeout")
                    || (message != null && (message.contains("超时") || message.toLowerCase().contains("timeout")))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
