package com.zhimu.server.biz.inspect.service.impl;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.DictQueryService;
import com.zhimu.server.admin.base.vo.BaseDictItemVO;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.common.api.BizRef;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.inspect.convert.DiagnosisConvert;
import com.zhimu.server.biz.inspect.convert.InspectionConvert;
import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.entity.AbnormalReportSymptom;
import com.zhimu.server.biz.inspect.entity.AiDiagnosis;
import com.zhimu.server.biz.inspect.entity.AiDiagnosisAdvice;
import com.zhimu.server.biz.inspect.entity.AiDiagnosisCause;
import com.zhimu.server.biz.inspect.entity.DiagnosisHandle;
import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.mapper.AbnormalReportMapper;
import com.zhimu.server.biz.inspect.mapper.AbnormalReportSymptomMapper;
import com.zhimu.server.biz.inspect.mapper.AiDiagnosisAdviceMapper;
import com.zhimu.server.biz.inspect.mapper.AiDiagnosisCauseMapper;
import com.zhimu.server.biz.inspect.mapper.AiDiagnosisMapper;
import com.zhimu.server.biz.inspect.mapper.DiagnosisHandleMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionMapper;
import com.zhimu.server.biz.inspect.query.FarmMemberRow;
import com.zhimu.server.biz.inspect.query.LinkableQuery;
import com.zhimu.server.biz.inspect.query.LinkableRow;
import com.zhimu.server.biz.inspect.query.ReportFormQuery;
import com.zhimu.server.biz.inspect.request.AbnormalReportCreateRequest;
import com.zhimu.server.biz.inspect.request.HandleSaveRequest;
import com.zhimu.server.biz.inspect.service.AbnormalReportService;
import com.zhimu.server.biz.inspect.service.DiagnosisService;
import com.zhimu.server.biz.inspect.support.InspectRules;
import com.zhimu.server.biz.inspect.support.InspectSupport;
import com.zhimu.server.biz.inspect.vo.DiagnosisDetailVO;
import com.zhimu.server.biz.inspect.vo.DiagnosisResultVO;
import com.zhimu.server.biz.inspect.vo.DiagnosisStateVO;
import com.zhimu.server.biz.inspect.vo.HandleSaveResultVO;
import com.zhimu.server.biz.inspect.vo.HandleVO;
import com.zhimu.server.biz.inspect.vo.LinkableReportVO;
import com.zhimu.server.biz.inspect.vo.ReportCreateResultVO;
import com.zhimu.server.biz.inspect.vo.ReportFormVO;
import com.zhimu.server.biz.inspect.vo.SymptomVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.common.vo.UserBriefVO;
import com.zhimu.server.framework.tx.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** API-I14–I16、I19–I22、I24、I26 实现。 */
@Service
@RequiredArgsConstructor
public class AbnormalReportServiceImpl implements AbnormalReportService {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M月d日");

    private final AbnormalReportMapper reportMapper;
    private final AbnormalReportSymptomMapper symptomMapper;
    private final AiDiagnosisMapper diagnosisMapper;
    private final AiDiagnosisCauseMapper causeMapper;
    private final AiDiagnosisAdviceMapper adviceMapper;
    private final DiagnosisHandleMapper handleMapper;
    private final InspectionMapper inspectionMapper;
    private final InspectSupport support;
    private final BizMediaService bizMediaService;
    private final DictQueryService dictQueryService;
    private final BaseDataQueryService baseDataQueryService;
    private final BreedQueryService breedQueryService;
    private final DiagnosisService diagnosisService;

    // ===== API-I14 上报表单 =====

    @Override
    public ReportFormVO formContext(ReportFormQuery query) {
        Inspection inspection = null;
        if (query != null && query.getInspectionId() != null) {
            inspection = requireInspectionForReport(query.getInspectionId());
        }
        ReportFormVO vo = new ReportFormVO();
        Long selectedPenId = inspection == null ? (query == null ? null : query.getPenId()) : inspection.getPenId();
        if (selectedPenId != null && inspection == null) {
            support.requirePen(selectedPenId);
        }
        vo.setSource(inspection == null ? "DIRECT" : "INSPECTION");
        vo.setInspectionId(inspection == null ? null : inspection.getId());
        if (inspection != null) {
            vo.setSourceText("已从今天 " + inspection.getRegisteredAt().toLocalTime()
                    .format(DateTimeFormatter.ofPattern("HH:mm")) + " 的巡检带入栏舍、批次和照片");
        }
        List<ReportFormVO.ReportPen> pens = reportPens(inspection == null ? null : inspection.getBatchId());
        vo.setPens(pens);
        ReportFormVO.ReportPrefill prefill = new ReportFormVO.ReportPrefill();
        if (inspection != null) {
            prefill.setPenId(inspection.getPenId());
            prefill.setBatchId(inspection.getBatchId());
            prefill.setDescription(inspection.getManualReason());
            prefill.setMedia(support.media(BizType.INSPECTION, inspection.getId()));
        } else if (selectedPenId != null) {
            prefill.setPenId(selectedPenId);
            for (ReportFormVO.ReportPen pen : pens) {
                if (pen.getPenId() == selectedPenId && pen.getBatches().size() == 1) {
                    prefill.setBatchId(pen.getBatches().get(0).getBatchId());
                }
            }
        }
        vo.setPrefill(prefill);
        Long symptomCategory = prefill.getBatchId() == null ? firstCategory(pens) : categoryOfBatch(pens,
                prefill.getBatchId());
        vo.setSymptoms(symptomsOfCategory(symptomCategory));
        vo.setNotifyNames(notifyNames(inspection));
        vo.setMaxMediaCount(6);
        vo.setMaxVideoCount(1);
        return vo;
    }

    // ===== API-I15 症状 =====

    @Override
    public List<SymptomVO> symptoms(Long batchId) {
        if (batchId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择批次");
        }
        BatchInfo batch = support.requireActiveBatch(batchId);
        checkBatchAccess(batch);
        return symptomsOfCategory(batch.categoryId());
    }

    // ===== API-I16 提交上报 =====

    @Override
    @Transactional
    public ReportCreateResultVO create(AbnormalReportCreateRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求内容不能为空");
        }
        FarmContext.FarmMember member = support.member();
        if (request.getClientRequestId() == null || request.getClientRequestId().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求编号不能为空");
        }
        if (request.getClientRequestId().length() > 64) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求编号不能超过 64 个字符");
        }
        AbnormalReport existing = reportMapper.selectByClientRequestId(support.farmId(),
                request.getClientRequestId());
        if (existing != null) {
            return createResult(existing);
        }
        if (request.getPenId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择栏舍");
        }
        if (request.getBatchId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择批次");
        }
        if (request.getSickCount() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写发病数量");
        }
        PenInfo pen = support.requirePen(request.getPenId());
        BatchInfo batch = support.requireActiveBatch(request.getBatchId());
        if (batch.penIds() == null || !batch.penIds().contains(request.getPenId())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这个栏舍没有在栏批次");
        }
        int stock = support.batchPenStock(batch.batchId(), request.getPenId());
        if (stock <= 0) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这个栏舍没有在栏批次");
        }
        if (request.getSickCount() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发病数量至少为 1 头");
        }
        if (request.getSickCount() > stock) {
            throw new BizException(ErrorCode.COUNT_EXCEEDED, "不能超过批次当前存栏 " + stock + " 头");
        }
        Inspection inspection = null;
        if (request.getInspectionId() != null) {
            inspection = requireInspectionForReport(request.getInspectionId());
            if (!Objects.equals(inspection.getPenId(), request.getPenId())
                    || (inspection.getBatchId() != null
                    && !Objects.equals(inspection.getBatchId(), request.getBatchId()))) {
                throw new BizException(ErrorCode.PARAM_INVALID, "巡检与上报的栏舍或批次不一致");
            }
        }
        List<Long> symptomIds = request.getSymptomIds() == null ? List.of() : request.getSymptomIds();
        List<SymptomVO> allowed = symptomsOfCategory(batch.categoryId());
        Map<Long, SymptomVO> allowedById = new LinkedHashMap<>();
        for (SymptomVO symptom : allowed) {
            allowedById.put(symptom.getSymptomId(), symptom);
        }
        List<SymptomVO> selected = new ArrayList<>();
        for (Long symptomId : symptomIds) {
            SymptomVO symptom = allowedById.get(symptomId);
            if (symptom == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请选择该批次品类的症状");
            }
            if (selected.stream().noneMatch(s -> s.getSymptomId() == symptomId)) {
                selected.add(symptom);
            }
        }
        selected.sort(java.util.Comparator.comparingInt(SymptomVO::getSortNo));
        String description = blankToNull(request.getDescription());
        if (description != null && description.length() > 500) {
            throw new BizException(ErrorCode.PARAM_INVALID, "描述不能超过 500 字");
        }
        if (selected.isEmpty() && (description == null || description.length() < 10)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请至少勾选一项症状，或描述不少于 10 个字");
        }
        LocalDateTime now = BeijingTime.now();
        LocalDate today = BeijingTime.today();
        AbnormalReport report = new AbnormalReport();
        report.setFarmId(support.farmId());
        report.setPenId(request.getPenId());
        report.setBatchId(request.getBatchId());
        report.setInspectionId(inspection == null ? null : inspection.getId());
        report.setSource(inspection == null ? "DIRECT" : "INSPECTION");
        report.setClientRequestId(request.getClientRequestId());
        report.setReporterId(member.userId());
        report.setReportedAt(now);
        report.setReportDate(today);
        report.setSickCount(request.getSickCount());
        report.setStockSnapshot(stock);
        report.setSickRate(InspectRules.sickRate(request.getSickCount(), stock));
        report.setDescription(description);
        report.setSameDaySeq(reportMapper.countSameDay(support.farmId(), request.getBatchId(), today) + 1);
        report.setStatus("PENDING");
        report.setAiState("DIAGNOSING");
        report.setAutoRetryUsed(false);
        report.setIsMajorEpidemic(false);
        report.setVersion(0);
        report.setDeleted(false);
        report.setUpdatedBy(member.userId());
        reportMapper.insert(report);

        int sort = 1;
        for (SymptomVO symptom : selected) {
            AbnormalReportSymptom entity = new AbnormalReportSymptom();
            entity.setFarmId(support.farmId());
            entity.setReportId(report.getId());
            entity.setSymptomId(symptom.getSymptomId());
            entity.setSymptomName(symptom.getName());
            entity.setSortNo(sort++);
            symptomMapper.insert(entity);
        }

        List<Long> media = request.getMediaFileIds();
        if (media != null && !media.isEmpty()) {
            bizMediaService.validateForBind(media, FileScene.ABNORMAL, member.userId(), "media_file_ids",
                    inspection == null ? new BizRef[0] : new BizRef[]{new BizRef(BizType.INSPECTION,
                            inspection.getId())});
            bizMediaService.replace(BizType.ABNORMAL_REPORT, report.getId(), support.farmId(), media);
        } else if (inspection != null) {
            bizMediaService.copy(BizType.INSPECTION, inspection.getId(), BizType.ABNORMAL_REPORT,
                    report.getId(), support.farmId());
        }

        if (inspection != null) {
            inspection.setReportId(report.getId());
            inspection.setUpdatedBy(member.userId());
            inspectionMapper.updateById(inspection);
        }
        AiDiagnosis attempt = diagnosisService.createAttempt(report, "SUBMIT", null);
        AfterCommit.run(() -> diagnosisService.executeAsync(attempt.getId()));
        return createResult(report);
    }

    @Override
    public ReportCreateResultVO findByRequest(String clientRequestId) {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        AbnormalReport report = reportMapper.selectByClientRequestId(support.farmId(), clientRequestId);
        if (report == null || !Objects.equals(report.getReporterId(), support.userId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return createResult(report);
    }

    // ===== API-I19 诊断详情 =====

    @Override
    public DiagnosisDetailVO detail(Long reportId) {
        AbnormalReport report = requireReportAccess(reportId);
        FarmContext.FarmMember member = support.member();
        DiagnosisDetailVO vo = new DiagnosisDetailVO();
        vo.setReportId(report.getId());
        vo.setVersion(report.getVersion() == null ? 0 : report.getVersion());
        vo.setStatus(report.getStatus());
        vo.setStatusText(DiagnosisConvert.reportStatusText(report.getStatus()));
        vo.setAiState(report.getAiState());
        vo.setAiStateText(DiagnosisConvert.aiStateText(report.getAiState()));

        DiagnosisDetailVO.DiagnosisReport reportVo = new DiagnosisDetailVO.DiagnosisReport();
        reportVo.setPenId(report.getPenId());
        reportVo.setPenName(support.penName(report.getPenId()));
        reportVo.setBatchId(report.getBatchId());
        BatchInfo batch = null;
        try {
            batch = support.requireActiveBatch(report.getBatchId());
        } catch (BizException ignored) {
            // 批次结束后仍可查看历史诊断
        }
        reportVo.setBatchNo(batch == null ? null : batch.batchNo());
        reportVo.setReporter(reporterVo(report.getReporterId()));
        reportVo.setReportedAt(BeijingTime.format(report.getReportedAt()));
        reportVo.setSource(report.getSource());
        reportVo.setInspectionId(report.getInspectionId());
        if (report.getInspectionId() != null) {
            Inspection inspection = inspectionMapper.selectById(report.getInspectionId());
            reportVo.setInspectionRegisteredAt(inspection == null ? null
                    : BeijingTime.format(inspection.getRegisteredAt()));
        }
        reportVo.setSickCount(report.getSickCount() == null ? 0 : report.getSickCount());
        reportVo.setStockSnapshot(report.getStockSnapshot() == null ? 0 : report.getStockSnapshot());
        reportVo.setSickRate(report.getSickRate());
        List<String> symptomNames = new ArrayList<>();
        for (AbnormalReportSymptom symptom : symptomMapper.selectByReportId(report.getId())) {
            symptomNames.add(symptom.getSymptomName());
        }
        reportVo.setSymptomNames(symptomNames);
        reportVo.setDescription(report.getDescription());
        List<MediaItemVO> media = support.media(BizType.ABNORMAL_REPORT, report.getId());
        reportVo.setMedia(media);
        reportVo.setMediaCount(media.size());
        vo.setReport(reportVo);

        AiDiagnosis diagnosis = diagnosisOf(report);
        if (diagnosis != null) {
            List<AiDiagnosisCause> causes = causeMapper.selectByDiagnosisId(diagnosis.getId());
            List<AiDiagnosisAdvice> advices = adviceMapper.selectByDiagnosisId(diagnosis.getId());
            vo.setDiagnosis(DiagnosisConvert.result(diagnosis, causes, advices));
        }
        DiagnosisHandle handle = handleMapper.selectByReportId(report.getId());
        if (handle != null) {
            vo.setHandle(DiagnosisConvert.handle(handle, support.userName(handle.getHandledBy()),
                    support.userName(handle.getUpdatedBy()), false, "CLOSED".equals(report.getStatus())));
        }
        AiDiagnosis latest = diagnosisMapper.selectLatestByReportId(report.getId());
        if (latest != null && latest.getFailReason() != null && !"SUCCESS".equals(latest.getStatus())) {
            DiagnosisDetailVO.FailureInfo failure = new DiagnosisDetailVO.FailureInfo();
            failure.setFailedAt(BeijingTime.format(latest.getFinishedAt()));
            failure.setReasonText(diagnosisService.lastFailureText(report));
            failure.setTriggerType(latest.getTriggerType());
            vo.setLastFailure(failure);
        }
        if (!"DONE".equals(report.getAiState())) {
            vo.setNoDiagnosisText("FAILED".equals(report.getAiState())
                    ? "AI 诊断失败，可以直接登记处理结果"
                    : "UNAVAILABLE".equals(report.getAiState())
                    ? "AI 诊断暂不可用，可以直接登记处理结果" : "AI 正在诊断，请稍候");
        }
        vo.setNotifiedNames(splitNames(report.getNotifiedNames()));
        if (diagnosis != null) {
            DiagnosisDetailVO.MedicationPrefill prefill = new DiagnosisDetailVO.MedicationPrefill();
            prefill.setPenId(report.getPenId());
            prefill.setBatchId(report.getBatchId());
            prefill.setReportId(report.getId());
            prefill.setDiagnosisId(diagnosis.getId());
            prefill.setReason("AI 诊断：疑似" + topCause(diagnosis));
            vo.setMedicationPrefill(prefill);
        }
        vo.setSteps(steps(report));
        vo.setPermissions(permissions(report, handle, member));
        return vo;
    }

    // ===== API-I20 状态 =====

    @Override
    public DiagnosisStateVO state(Long reportId) {
        AbnormalReport report = requireReportAccess(reportId);
        return DiagnosisConvert.state(report, diagnosisService.lastFailureText(report));
    }

    // ===== API-I21 重新诊断 =====

    @Override
    @Transactional
    public DiagnosisStateVO rediagnose(Long reportId) {
        AbnormalReport report = requireReportAccess(reportId);
        FarmContext.FarmMember member = support.member();
        boolean allowed = member.isManagerView() || Objects.equals(report.getReporterId(), member.userId());
        if (!allowed) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN,
                    "只有上报人、所有者和场长可以重新诊断");
        }
        if ("DIAGNOSING".equals(report.getAiState())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "AI 正在诊断，请稍候");
        }
        if (!"PENDING".equals(report.getStatus()) || "DONE".equals(report.getAiState())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "已有诊断结果或已登记处理结果，不需要重新诊断");
        }
        AiDiagnosis attempt = diagnosisService.createAttempt(report, "MANUAL_RETRY", member.userId());
        AfterCommit.run(() -> diagnosisService.executeAsync(attempt.getId()));
        return DiagnosisConvert.state(report, diagnosisService.lastFailureText(report));
    }

    // ===== API-I22 处理结果 =====

    @Override
    @Transactional
    public HandleSaveResultVO saveHandle(Long reportId, HandleSaveRequest request) {
        support.requireManager("只有所有者和场长可以登记处理结果");
        AbnormalReport report = requireReportAccess(reportId);
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求内容不能为空");
        }
        if ("CLOSED".equals(report.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这条记录已结案，不能再修改");
        }
        boolean canHandle = "DIAGNOSED".equals(report.getStatus())
                || "HANDLED".equals(report.getStatus())
                || ("PENDING".equals(report.getStatus()) && "FAILED".equals(report.getAiState()));
        if (!canHandle) {
            throw new BizException(ErrorCode.STATE_CHANGED,
                    "AI 正在诊断，诊断完成或诊断失败后才能登记处理结果");
        }
        String diagnostician = blankToNull(request.getDiagnostician());
        if (diagnostician == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写诊断人");
        }
        if (diagnostician.length() > 20) {
            throw new BizException(ErrorCode.PARAM_INVALID, "诊断人不能超过 20 个字");
        }
        String treatment = blankToNull(request.getTreatment());
        if (treatment == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写处理方式");
        }
        if (treatment.length() > 200) {
            throw new BizException(ErrorCode.PARAM_INVALID, "处理方式不能超过 200 个字");
        }
        if (request.getUsedDrug() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择是否使用药品");
        }
        int cured = request.getCuredCount() == null ? 0 : request.getCuredCount();
        int culled = request.getCulledCount() == null ? 0 : request.getCulledCount();
        if (cured < 0 || culled < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "治愈和死淘数量不能为负数");
        }
        int sickCount = report.getSickCount() == null ? 0 : report.getSickCount();
        if (cured + culled > sickCount) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "治愈和死淘合计不能超过发病数量 " + sickCount + " 头");
        }
        boolean hasDiagnosis = diagnosisOf(report) != null;
        String aiMatch = request.getAiMatch();
        if (hasDiagnosis && !"MATCH".equals(aiMatch) && !"NOT_MATCH".equals(aiMatch)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择 AI 诊断是否相符");
        }
        if (!hasDiagnosis) {
            aiMatch = "NONE";
        }
        if (Boolean.TRUE.equals(request.getToMedication()) && !Boolean.TRUE.equals(request.getUsedDrug())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转为用药记录前请选择使用药品");
        }
        boolean close = cured + culled == sickCount;
        if (close && !Boolean.TRUE.equals(request.getConfirmClose())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "治愈和死淘合计已达到发病数量 " + sickCount + " 头，保存后记录自动结案，不能再修改",
                    Map.of("field_errors", List.of(new com.zhimu.server.common.error.FieldErrorVO(
                            "confirm_close",
                            "治愈和死淘合计已达到发病数量 " + sickCount + " 头，保存后记录自动结案，不能再修改"))));
        }
        DiagnosisHandle handle = handleMapper.selectByReportId(report.getId());
        FarmContext.FarmMember member = support.member();
        if (handle == null) {
            handle = new DiagnosisHandle();
            handle.setFarmId(support.farmId());
            handle.setReportId(report.getId());
            handle.setHandledBy(member.userId());
            handle.setHandledAt(BeijingTime.now());
            handle.setVersion(0);
            handle.setDiagnostician(diagnostician);
            handle.setTreatment(treatment);
            handle.setUsedDrug(Boolean.TRUE.equals(request.getUsedDrug()));
            handle.setCuredCount(cured);
            handle.setCulledCount(culled);
            handle.setAiMatch(aiMatch);
            handle.setUpdatedBy(member.userId());
            handleMapper.insert(handle);
        } else {
            if (request.getVersion() != null && !request.getVersion().equals(handle.getVersion())) {
                throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
            }
            handle.setDiagnostician(diagnostician);
            handle.setTreatment(treatment);
            handle.setUsedDrug(Boolean.TRUE.equals(request.getUsedDrug()));
            handle.setCuredCount(cured);
            handle.setCulledCount(culled);
            handle.setAiMatch(aiMatch);
            handle.setUpdatedBy(member.userId());
            handleMapper.updateById(handle);
        }
        report.setStatus(close ? "CLOSED" : "HANDLED");
        if (report.getHandledAt() == null) {
            report.setHandledAt(BeijingTime.now());
        }
        if (close) {
            report.setClosedAt(BeijingTime.now());
        }
        report.setUpdatedBy(member.userId());
        reportMapper.updateById(report);

        HandleSaveResultVO vo = new HandleSaveResultVO();
        vo.setReportId(report.getId());
        vo.setHandleId(handle.getId());
        vo.setStatus(report.getStatus());
        vo.setClosed(close);
        vo.setCulledCount(culled);
        vo.setVersion(handle.getVersion() == null ? 0 : handle.getVersion());
        if (culled > 0) {
            HandleSaveResultVO.DeathPrompt prompt = new HandleSaveResultVO.DeathPrompt();
            prompt.setPenId(report.getPenId());
            prompt.setBatchId(report.getBatchId());
            prompt.setCulledCount(culled);
            vo.setDeathPrompt(prompt);
        }
        if (Boolean.TRUE.equals(request.getToMedication())) {
            DiagnosisDetailVO.MedicationPrefill prefill = new DiagnosisDetailVO.MedicationPrefill();
            prefill.setPenId(report.getPenId());
            prefill.setBatchId(report.getBatchId());
            prefill.setReportId(report.getId());
            AiDiagnosis diagnosis = diagnosisOf(report);
            prefill.setDiagnosisId(diagnosis == null ? null : diagnosis.getId());
            prefill.setReason("AI 诊断：疑似" + (diagnosis == null ? "异常" : topCause(diagnosis)));
            vo.setMedicationPrefill(prefill);
        }
        return vo;
    }

    // ===== API-I24 可关联上报 =====

    @Override
    public List<LinkableReportVO> linkable(LinkableQuery query) {
        if (query == null || (query.getBatchId() == null && query.getPenId() == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择批次或栏舍");
        }
        if (query.getPenId() != null) {
            support.requirePen(query.getPenId());
        }
        List<LinkableRow> rows = reportMapper.selectLinkable(support.farmId(), query.getBatchId(),
                query.getPenId(), BeijingTime.now().minusDays(30));
        List<LinkableReportVO> result = new ArrayList<>();
        FarmContext.FarmMember member = support.member();
        for (LinkableRow row : rows) {
            if (!member.canAccessPen(row.getPenId())) {
                continue;
            }
            LinkableReportVO vo = new LinkableReportVO();
            vo.setReportId(row.getReportId());
            vo.setDiagnosisId(row.getDiagnosisId());
            vo.setReportedAt(BeijingTime.format(row.getReportedAt()));
            vo.setPenId(row.getPenId());
            vo.setPenName(row.getPenName());
            vo.setBatchId(row.getBatchId());
            vo.setBatchNo(row.getBatchNo());
            vo.setTopCauseName(row.getTopCauseName());
            vo.setStatus(row.getStatus());
            String date = row.getReportedAt() == null ? "" : row.getReportedAt().format(MONTH_DAY);
            vo.setLabel(date + " AI 诊断 · " + row.getPenName());
            result.add(vo);
        }
        return result;
    }

    // ===== 共用 =====

    private AbnormalReport requireReportAccess(Long reportId) {
        if (reportId == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        AbnormalReport report = reportMapper.selectById(reportId);
        if (report == null || !support.farmId().equals(report.getFarmId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        FarmContext.FarmMember member = support.member();
        if (member.isStaff() && !Objects.equals(report.getReporterId(), member.userId())) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN,
                    "你没有这条诊断记录的查看权限。员工只能查看自己上报的诊断。");
        }
        FarmContext.checkPen(report.getPenId());
        return report;
    }

    private Inspection requireInspectionForReport(Long inspectionId) {
        Inspection inspection = inspectionMapper.selectById(inspectionId);
        if (inspection == null || !support.farmId().equals(inspection.getFarmId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        FarmContext.checkPen(inspection.getPenId());
        if (Boolean.TRUE.equals(inspection.getIsBackfill())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "补录记录不能转上报，如需上报请从快捷登记进入异常上报。");
        }
        if (!BeijingTime.today().equals(inspection.getInspectDate())
                || !Boolean.TRUE.equals(inspection.getIsAbnormal())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能把今天的异常巡检转上报");
        }
        if (inspection.getReportId() != null) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这条巡检已上报")
                    .with("report_id", inspection.getReportId());
        }
        return inspection;
    }

    private List<ReportFormVO.ReportPen> reportPens(Long prefillBatchId) {
        List<ReportFormVO.ReportPen> result = new ArrayList<>();
        for (PenInfo pen : support.pens()) {
            List<BatchInfo> batches = support.batchesOfPen(pen.penId());
            if (batches.isEmpty()) {
                continue;
            }
            ReportFormVO.ReportPen reportPen = new ReportFormVO.ReportPen();
            reportPen.setPenId(pen.penId());
            reportPen.setPenName(pen.penName());
            List<ReportFormVO.ReportBatch> batchVos = new ArrayList<>();
            for (BatchInfo batch : batches) {
                int stock = support.batchPenStock(batch.batchId(), pen.penId());
                if (stock <= 0) {
                    continue;
                }
                ReportFormVO.ReportBatch batchVo = new ReportFormVO.ReportBatch();
                batchVo.setBatchId(batch.batchId());
                batchVo.setBatchNo(batch.batchNo());
                batchVo.setCategoryId(batch.categoryId() == null ? 0 : batch.categoryId());
                batchVo.setStockCount(stock);
                batchVo.setBreedTitle(breedTitle(batch));
                batchVo.setDayAge(dayAge(batch));
                batchVos.add(batchVo);
            }
            if (!batchVos.isEmpty()) {
                reportPen.setBatches(batchVos);
                result.add(reportPen);
            }
        }
        return result;
    }

    private List<SymptomVO> symptomsOfCategory(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        List<BaseDictItemVO> items = dictQueryService.listForApp("SYMPTOM", null, categoryId,
                support.farmId());
        List<SymptomVO> result = new ArrayList<>();
        for (BaseDictItemVO item : items) {
            SymptomVO vo = new SymptomVO();
            vo.setSymptomId(item.getId() == null ? 0 : item.getId());
            vo.setName(item.getName());
            vo.setSortNo(item.getSortNo() == null ? 0 : item.getSortNo());
            result.add(vo);
        }
        result.sort(java.util.Comparator.comparingInt(SymptomVO::getSortNo));
        return result;
    }

    private List<String> notifyNames(Inspection inspection) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (inspection != null) {
            names.add(support.userName(inspection.getRegistrantId()));
        }
        for (FarmMemberRow row : support.memberList()) {
            if ("OWNER".equals(row.getRole()) || "MANAGER".equals(row.getRole())) {
                names.add(row.getName());
            }
        }
        names.remove(null);
        return new ArrayList<>(names);
    }

    private void checkBatchAccess(BatchInfo batch) {
        if (batch.penIds() == null || batch.penIds().isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        FarmContext.FarmMember member = support.member();
        boolean allowed = batch.penIds().stream().anyMatch(member::canAccessPen);
        if (!allowed) {
            throw new BizException(ErrorCode.PEN_FORBIDDEN);
        }
    }

    private String breedTitle(BatchInfo batch) {
        String breed = null;
        if (batch.breedId() != null) {
            BreedQueryService.BreedInfo info = breedQueryService.info(batch.breedId());
            breed = info == null ? null : info.name();
        }
        CategoryInfo category = batch.categoryId() == null ? null
                : baseDataQueryService.getCategory(batch.categoryId());
        if (breed == null) {
            return category == null ? null : category.name();
        }
        return category == null ? breed : breed + category.name();
    }

    private Integer dayAge(BatchInfo batch) {
        if (batch.entryAgeDays() == null || batch.entryDate() == null) {
            return null;
        }
        return batch.entryAgeDays() + (int) ChronoUnit.DAYS.between(batch.entryDate(), BeijingTime.today());
    }

    private Long firstCategory(List<ReportFormVO.ReportPen> pens) {
        for (ReportFormVO.ReportPen pen : pens) {
            if (!pen.getBatches().isEmpty()) {
                return pen.getBatches().get(0).getCategoryId();
            }
        }
        return null;
    }

    private Long categoryOfBatch(List<ReportFormVO.ReportPen> pens, Long batchId) {
        for (ReportFormVO.ReportPen pen : pens) {
            for (ReportFormVO.ReportBatch batch : pen.getBatches()) {
                if (batch.getBatchId() == batchId) {
                    return batch.getCategoryId();
                }
            }
        }
        return firstCategory(pens);
    }

    private ReportCreateResultVO createResult(AbnormalReport report) {
        ReportCreateResultVO vo = new ReportCreateResultVO();
        vo.setReportId(report.getId());
        vo.setStatus(report.getStatus());
        vo.setAiState(report.getAiState());
        vo.setPollIntervalSec(3);
        vo.setPollMaxSec(90);
        return vo;
    }

    private AiDiagnosis diagnosisOf(AbnormalReport report) {
        if (report.getCurrentDiagnosisId() != null) {
            AiDiagnosis diagnosis = diagnosisMapper.selectById(report.getCurrentDiagnosisId());
            if (diagnosis != null) {
                return diagnosis;
            }
        }
        return diagnosisMapper.selectSuccessByReportId(report.getId());
    }

    private List<DiagnosisDetailVO.DiagnosisStep> steps(AbnormalReport report) {
        List<DiagnosisDetailVO.DiagnosisStep> steps = new ArrayList<>();
        String status = report.getStatus() == null ? "PENDING" : report.getStatus();
        boolean pending = "PENDING".equals(status);
        boolean handled = "HANDLED".equals(status);
        boolean closed = "CLOSED".equals(status);
        steps.add(step("PENDING", pending && !"DONE".equals(report.getAiState()) ? "CURRENT" : "DONE",
                BeijingTime.format(report.getReportedAt())));
        steps.add(step("DIAGNOSED", pending ? "TODO" : closed || handled ? "DONE" : "CURRENT",
                BeijingTime.format(report.getDiagnosedAt())));
        steps.add(step("HANDLED", handled ? "CURRENT" : closed ? "DONE" : "TODO",
                BeijingTime.format(report.getHandledAt())));
        steps.add(step("CLOSED", closed ? "DONE" : "TODO", BeijingTime.format(report.getClosedAt())));
        return steps;
    }

    private static DiagnosisDetailVO.DiagnosisStep step(String code, String state, String time) {
        DiagnosisDetailVO.DiagnosisStep step = new DiagnosisDetailVO.DiagnosisStep();
        step.setCode(code);
        step.setState(state);
        step.setTime(time);
        return step;
    }

    private DiagnosisDetailVO.DiagnosisPermissions permissions(AbnormalReport report, DiagnosisHandle handle,
                                                               FarmContext.FarmMember member) {
        DiagnosisDetailVO.DiagnosisPermissions permissions = new DiagnosisDetailVO.DiagnosisPermissions();
        boolean manager = member.isManagerView();
        boolean canHandle = manager && ("DIAGNOSED".equals(report.getStatus())
                || "HANDLED".equals(report.getStatus())
                || ("PENDING".equals(report.getStatus()) && "FAILED".equals(report.getAiState())));
        permissions.setCanHandle(canHandle);
        permissions.setCanModifyHandle(canHandle);
        permissions.setCanRediagnose((manager || Objects.equals(report.getReporterId(), member.userId()))
                && "PENDING".equals(report.getStatus())
                && ("UNAVAILABLE".equals(report.getAiState()) || "FAILED".equals(report.getAiState())));
        permissions.setCanViewInspection(report.getInspectionId() != null);
        permissions.setCanViewRevisions(false);
        permissions.setCanConvertMedication(handle != null && Boolean.TRUE.equals(handle.getUsedDrug()));
        return permissions;
    }

    private UserBriefVO reporterVo(Long userId) {
        if (userId == null) {
            return null;
        }
        UserBriefVO vo = InspectionConvert.userBrief(userId, support.memberMap());
        if (vo.getName() == null) {
            vo.setName(support.userName(userId));
        }
        return vo;
    }

    private static List<String> splitNames(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("、"));
    }

    private String topCause(AiDiagnosis diagnosis) {
        if (diagnosis == null) {
            return "异常";
        }
        List<AiDiagnosisCause> causes = causeMapper.selectByDiagnosisId(diagnosis.getId());
        return causes == null || causes.isEmpty() || causes.get(0).getDiseaseName() == null
                ? "异常" : causes.get(0).getDiseaseName();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
