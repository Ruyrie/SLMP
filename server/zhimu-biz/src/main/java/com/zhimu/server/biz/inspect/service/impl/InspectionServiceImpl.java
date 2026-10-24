package com.zhimu.server.biz.inspect.service.impl;

import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.common.api.BizRef;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.inspect.convert.InspectionConvert;
import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.entity.InspectionItemResult;
import com.zhimu.server.biz.inspect.mapper.InspectionItemResultMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionMapper;
import com.zhimu.server.biz.inspect.query.BatchPenStockRow;
import com.zhimu.server.biz.inspect.query.FarmGeoRow;
import com.zhimu.server.biz.inspect.query.FarmMemberRow;
import com.zhimu.server.biz.inspect.query.InspectionDayQuery;
import com.zhimu.server.biz.inspect.query.InspectionFormQuery;
import com.zhimu.server.biz.inspect.query.PenHistoryQuery;
import com.zhimu.server.biz.inspect.query.PenResponsibleRow;
import com.zhimu.server.biz.inspect.request.InspectionBackfillRequest;
import com.zhimu.server.biz.inspect.request.InspectionSubmitRequest;
import com.zhimu.server.biz.inspect.service.InspectionService;
import com.zhimu.server.biz.inspect.support.GeoDistance;
import com.zhimu.server.biz.inspect.support.InspectRules;
import com.zhimu.server.biz.inspect.support.InspectSupport;
import com.zhimu.server.biz.inspect.support.InspectTemplateBundle;
import com.zhimu.server.biz.inspect.vo.InspectionDetailVO;
import com.zhimu.server.biz.inspect.vo.InspectionFormVO;
import com.zhimu.server.biz.inspect.vo.InspectionListItemVO;
import com.zhimu.server.biz.inspect.vo.InspectionSubmitResultVO;
import com.zhimu.server.biz.inspect.vo.InspectionSummaryVO;
import com.zhimu.server.biz.inspect.vo.PenInspectionOverviewVO;
import com.zhimu.server.biz.inspect.vo.PenInspectionRowVO;
import com.zhimu.server.biz.inspect.vo.RecentIssueVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.common.vo.OptionVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** API-I01–I03、I06–I12、I25 实现。 */
@Service
@RequiredArgsConstructor
public class InspectionServiceImpl implements InspectionService {

    private final InspectionMapper inspectionMapper;
    private final InspectionItemResultMapper itemResultMapper;
    private final InspectSupport support;
    private final BizMediaService bizMediaService;

    // ===== API-I01 统计 =====

    @Override
    public InspectionSummaryVO summary(String date) {
        LocalDate day = InspectRules.parseDate(date, BeijingTime.today());
        requireNotFuture(day);
        FarmContext.FarmMember member = support.member();
        List<RowData> rows = dayRows(day);
        int done = 0;
        int pending = 0;
        int abnormal = 0;
        int missed = 0;
        int noOwnerPending = 0;
        RowData firstAbnormal = null;
        for (RowData row : rows) {
            switch (row.displayStatus()) {
                case "ABNORMAL" -> {
                    abnormal++;
                    done++;
                    if (firstAbnormal == null) {
                        firstAbnormal = row;
                    }
                }
                case "DONE" -> done++;
                case "MISSED" -> missed++;
                default -> {
                    pending++;
                    if (row.responsibleNames().isEmpty()) {
                        noOwnerPending++;
                    }
                }
            }
        }
        LocalTime deadline = support.deadline();
        InspectionSummaryVO vo = new InspectionSummaryVO();
        vo.setFarmName(member.farmName());
        vo.setDate(BeijingTime.format(day));
        vo.setDeadlineTime(InspectRules.formatTime(deadline));
        vo.setRemindTime(InspectRules.formatTime(InspectRules.remindTime(deadline, null)));
        vo.setScope(member.isStaff() ? "MY_PENS" : "ALL_PENS");
        vo.setShouldCount(rows.size());
        vo.setDoneCount(done);
        vo.setPendingCount(pending);
        vo.setAbnormalCount(abnormal);
        vo.setMissedCount(missed);
        vo.setCompletionRate(rows.isEmpty() ? null
                : BigDecimal.valueOf(done).divide(BigDecimal.valueOf(rows.size()), 4, RoundingMode.HALF_UP));
        vo.setDeadlinePassed(InspectRules.deadlinePassed(day, deadline, BeijingTime.now()));
        vo.setAllDone(!rows.isEmpty() && pending == 0 && missed == 0);
        vo.setCanEditDeadline(member.isManagerView());
        vo.setNoOwnerPendingCount(member.isManagerView() ? noOwnerPending : null);
        vo.setResponsiblePenCount(member.isStaff() && member.penIds() != null ? member.penIds().size() : null);
        if (firstAbnormal != null) {
            vo.setFirstAbnormalPenName(firstAbnormal.pen().penName());
            vo.setFirstAbnormalReported(firstAbnormal.inspection() != null
                    && firstAbnormal.inspection().getReportId() != null);
        }
        return vo;
    }

    // ===== API-I02 某日栏舍清单 =====

    @Override
    public PageVO<InspectionListItemVO> dayList(InspectionDayQuery query) {
        LocalDate day = InspectRules.parseDate(query == null ? null : query.getDate(), BeijingTime.today());
        requireNotFuture(day);
        int page = page(query == null ? null : query.getPage());
        int size = size(query == null ? null : query.getSize());
        String status = query == null || query.getStatus() == null || query.getStatus().isBlank()
                ? "ALL" : query.getStatus().toUpperCase();
        List<RowData> rows = dayRows(day);
        int all = rows.size();
        int pending = (int) rows.stream().filter(r -> "PENDING".equals(r.displayStatus())).count();
        int abnormal = (int) rows.stream().filter(r -> "ABNORMAL".equals(r.displayStatus())).count();
        int done = (int) rows.stream().filter(r -> "DONE".equals(r.displayStatus())).count();
        List<RowData> filtered = switch (status) {
            case "PENDING" -> rows.stream().filter(r -> "PENDING".equals(r.displayStatus())).toList();
            case "ABNORMAL" -> rows.stream().filter(r -> "ABNORMAL".equals(r.displayStatus())).toList();
            case "DONE" -> rows.stream().filter(r -> "DONE".equals(r.displayStatus())).toList();
            default -> rows;
        };
        int total = filtered.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        List<InspectionListItemVO> list = new ArrayList<>();
        for (RowData row : filtered.subList(from, to)) {
            list.add(listItem(row, day));
        }
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("ALL", all);
        tabs.put("PENDING", pending);
        tabs.put("ABNORMAL", abnormal);
        tabs.put("DONE", done);
        return PageVO.of(list, total, page, size).withTabCounts(tabs);
    }

    // ===== API-I03 前 3 天问题 =====

    @Override
    public List<RecentIssueVO> recentIssues() {
        support.requireManager("只有所有者和场长可以查看超时与补录");
        LocalDate today = BeijingTime.today();
        List<Inspection> issues = inspectionMapper.selectIssues(support.farmId(),
                today.minusDays(InspectRules.BACKFILL_DAYS), today.minusDays(1));
        Map<Long, FarmMemberRow> members = support.memberMap();
        List<RecentIssueVO> result = new ArrayList<>();
        if (issues == null) {
            return result;
        }
        for (Inspection inspection : issues) {
            String type;
            if (InspectRules.MISSED.equals(inspection.getInspectStatus())) {
                type = "MISSED";
            } else if (Boolean.TRUE.equals(inspection.getIsOverdue())) {
                type = "OVERDUE";
            } else if (Boolean.TRUE.equals(inspection.getIsBackfill())) {
                type = "BACKFILL";
            } else {
                continue;
            }
            FarmMemberRow registrant = inspection.getRegistrantId() == null ? null
                    : members.get(inspection.getRegistrantId());
            result.add(InspectionConvert.recentIssue(inspection, support.penName(inspection.getPenId()),
                    registrant == null ? null : registrant.getName(), type));
        }
        return result;
    }

    // ===== API-I06 表单上下文 =====

    @Override
    public InspectionFormVO formContext(InspectionFormQuery query) {
        if (query == null || query.getPenId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择栏舍");
        }
        String mode = query.getMode() == null || query.getMode().isBlank() ? "REGISTER"
                : query.getMode().toUpperCase();
        if (!Set.of("REGISTER", "MODIFY", "BACKFILL").contains(mode)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "表单模式不正确");
        }
        Long penId = query.getPenId();
        PenInfo pen = support.requirePen(penId);
        List<BatchInfo> batches = support.batchesOfPen(penId);
        if ("REGISTER".equals(mode) && batches.isEmpty()) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这个栏舍已没有在栏批次，不需要巡检");
        }
        if ("BACKFILL".equals(mode)) {
            support.requireManager("只有所有者和场长可以补录");
        }
        LocalDate today = BeijingTime.today();
        LocalDate inspectDate = InspectRules.parseDate(query.getInspectDate(),
                "BACKFILL".equals(mode) ? defaultBackfillDate(penId) : today);
        Inspection inspection = null;
        if ("MODIFY".equals(mode)) {
            if (query.getInspectionId() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请选择要修改的巡检记录");
            }
            inspection = requireInspection(query.getInspectionId());
            if (!inspection.getPenId().equals(penId)) {
                throw new BizException(ErrorCode.NOT_FOUND);
            }
            if (!InspectRules.DONE.equals(inspection.getInspectStatus())) {
                throw new BizException(ErrorCode.STATE_CHANGED, "这条巡检还没有登记，请先登记");
            }
            FarmContext.FarmMember member = support.member();
            if (member.isStaff() && (!Objects.equals(inspection.getRegistrantId(), member.userId())
                    || !today.equals(inspection.getInspectDate()))) {
                throw new BizException(ErrorCode.ROLE_FORBIDDEN, "只能修改自己当天登记的巡检");
            }
            if (!member.isStaff() && !InspectRules.withinEditWindow(inspection.getInspectDate())) {
                throw new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED,
                        "这条记录已超过 3 天，修改需要提交修改申请");
            }
            inspectDate = inspection.getInspectDate();
            Long inspectionBatchId = inspection.getBatchId();
            if (inspectionBatchId != null && batches.stream()
                    .noneMatch(batch -> batch.batchId().equals(inspectionBatchId))) {
                try {
                    batches = new ArrayList<>(batches);
                    batches.add(0, support.requireActiveBatch(inspectionBatchId));
                } catch (BizException ignored) {
                    // 批次已结束仍可查看历史记录，表单批次列表保持当前在栏批次
                }
            }
        } else if ("BACKFILL".equals(mode)) {
            if (!InspectRules.inBackfillWindow(inspectDate)) {
                throw new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED, "只能补录前 3 天内的漏巡");
            }
            Inspection existing = inspectionMapper.selectByPenDate(support.farmId(), penId, inspectDate);
            if (existing != null && InspectRules.DONE.equals(existing.getInspectStatus())) {
                throw new BizException(ErrorCode.STATE_CHANGED,
                        (inspectDate.getMonthValue()) + "月" + inspectDate.getDayOfMonth() + "日 已有巡检记录，可在巡检历史中修改");
            }
        } else {
            inspection = inspectionMapper.selectByPenDate(support.farmId(), penId, today);
            if (inspection != null && InspectRules.CANCELLED.equals(inspection.getInspectStatus())) {
                inspection = null;
            }
        }

        InspectionFormVO vo = new InspectionFormVO();
        vo.setMode(mode);
        InspectionFormVO.FormPen formPen = new InspectionFormVO.FormPen();
        formPen.setPenId(pen.penId());
        formPen.setPenCode(pen.penCode());
        formPen.setPenName(pen.penName());
        formPen.setResponsibleNames(namesOfPen(penId));
        vo.setPen(formPen);
        List<InspectionFormVO.FormBatch> formBatches = new ArrayList<>();
        int stock = 0;
        for (BatchInfo batch : batches) {
            InspectionFormVO.FormBatch formBatch = new InspectionFormVO.FormBatch();
            formBatch.setBatchId(batch.batchId());
            formBatch.setBatchNo(batch.batchNo());
            int batchStock = support.batchPenStock(batch.batchId(), penId);
            formBatch.setStockCount(batchStock);
            stock += batchStock;
            formBatches.add(formBatch);
        }
        vo.setBatches(formBatches);
        vo.setStockCount(stock);
        vo.setCategoryId(batches.isEmpty() ? 0L : batches.get(0).categoryId());
        vo.setInspectDate(BeijingTime.format(inspectDate));
        FarmContext.FarmMember member = support.member();
        vo.setRegistrant(InspectionConvert.userBrief(member.userId(), support.memberMap()));
        vo.setCanChooseExecutor(member.isManagerView());
        vo.setCanManualMark(member.isManagerView());
        vo.setMembers(member.isManagerView() ? memberOptions() : List.of());
        vo.setDefaultExecutorId(inspection != null && inspection.getExecutorId() != null
                ? inspection.getExecutorId() : member.userId());
        if (inspection != null && inspection.getTemplateId() != null && inspection.getTemplateVersion() != null) {
            vo.setTemplate(templateVo(support.templateForVersion(inspection.getTemplateId(),
                    inspection.getTemplateVersion())));
        } else if (!batches.isEmpty()) {
            vo.setTemplate(templateVo(support.templateForCategory(batches.get(0).categoryId())));
        }
        LocalTime deadline = support.deadline();
        vo.setDeadlineTime(InspectRules.formatTime(deadline));
        vo.setDeadlinePassed(InspectRules.deadlinePassed(inspectDate, deadline, BeijingTime.now()));
        vo.setFarmLatitude(doubleValue(support.farmGeo() == null ? null : support.farmGeo().getLatitude()));
        vo.setFarmLongitude(doubleValue(support.farmGeo() == null ? null : support.farmGeo().getLongitude()));
        if ("REGISTER".equals(mode) && inspection != null && InspectRules.DONE.equals(inspection.getInspectStatus())) {
            InspectionFormVO.RegisteredInfo registered = new InspectionFormVO.RegisteredInfo();
            registered.setInspectionId(inspection.getId());
            FarmMemberRow registrant = support.memberMap().get(inspection.getRegistrantId());
            registered.setRegistrantName(registrant == null ? null : registrant.getName());
            registered.setRegisteredAt(BeijingTime.format(inspection.getRegisteredAt()));
            vo.setAlreadyRegistered(registered);
        }
        if (inspection != null) {
            InspectionDetailVO record = detailOf(inspection, false);
            vo.setRecord(record);
            vo.setCanReport(canReport(inspection));
            if (inspection.getReportId() != null) {
                vo.setAbnormalLockedReason("REPORTED");
            } else if ("CHECK".equals(inspection.getAbnormalSource())) {
                vo.setAbnormalLockedReason("CHECK");
            }
        }
        if ("BACKFILL".equals(mode)) {
            vo.setBackfillDates(backfillDates(penId));
            InspectionFormVO.DateRange range = new InspectionFormVO.DateRange();
            range.setFrom(BeijingTime.format(today.minusDays(InspectRules.BACKFILL_DAYS)));
            range.setTo(BeijingTime.format(today.minusDays(1)));
            vo.setBackfillRange(range);
        }
        return vo;
    }

    // ===== API-I07 登记 =====

    @Override
    @Transactional
    public InspectionSubmitResultVO register(InspectionSubmitRequest request) {
        requireRequest(request);
        FarmContext.FarmMember member = support.member();
        Long penId = request.getPenId();
        PenInfo pen = support.requirePen(penId);
        if (request.getClientRequestId() == null || request.getClientRequestId().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求编号不能为空");
        }
        if (request.getClientRequestId().length() > 64) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求编号不能超过 64 个字符");
        }
        Inspection byRequest = inspectionMapper.selectByClientRequestId(support.farmId(),
                request.getClientRequestId());
        if (byRequest != null) {
            return InspectionConvert.submitResult(byRequest, support.penName(byRequest.getPenId()));
        }
        List<BatchInfo> batches = support.batchesOfPen(penId);
        if (batches.isEmpty()) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这个栏舍已没有在栏批次，不需要巡检");
        }
        LocalDate today = BeijingTime.today();
        Inspection existing = inspectionMapper.selectByPenDate(support.farmId(), penId, today);
        if (existing != null && InspectRules.DONE.equals(existing.getInspectStatus())) {
            if (request.getVersion() == null) {
                FarmMemberRow registrant = support.memberMap().get(existing.getRegistrantId());
                throw new BizException(ErrorCode.STATE_CHANGED,
                        pen.penName() + "今天已由" + (registrant == null ? "他人" : registrant.getName())
                                + "在" + BeijingTime.format(existing.getRegisteredAt()) + "登记，请刷新后修改")
                        .with("inspection_id", existing.getId());
            }
            return modify(existing.getId(), request);
        }
        BatchInfo primary = uniqueBatch(batches);
        InspectTemplateBundle template = support.templateForCategory(primary != null ? primary.categoryId()
                : batches.get(0).categoryId());
        SubmitInput input = new SubmitInput(request.getItemResults(), request.getIsManualMarked(),
                request.getManualReason(), request.getRemark(), request.getExecutorId(), request.getLocation(),
                request.getMediaFileIds());
        Submission submission = evaluate(input, template, false);
        LocalTime deadline = support.deadline();
        boolean overdue = InspectRules.deadlinePassed(today, deadline, BeijingTime.now());
        Inspection inspection = existing != null ? existing : new Inspection();
        inspection.setFarmId(support.farmId());
        inspection.setPenId(penId);
        inspection.setBatchId(primary == null ? null : primary.batchId());
        inspection.setInspectDate(today);
        inspection.setInspectStatus(InspectRules.DONE);
        inspection.setTemplateId(template.templateId);
        inspection.setTemplateVersion(template.version);
        inspection.setStockSnapshot(totalStock(batches, penId));
        inspection.setRegistrantId(member.userId());
        inspection.setExecutorId(resolveExecutor(request.getExecutorId(), member));
        inspection.setRegisteredAt(BeijingTime.now());
        inspection.setClientRequestId(request.getClientRequestId());
        inspection.setDeadlineTime(deadline);
        inspection.setIsOverdue(overdue);
        inspection.setIsBackfill(false);
        applyLocation(inspection, request.getLocation());
        applySubmission(inspection, submission, member);
        inspection.setVersion(existing == null ? 0 : existing.getVersion());
        inspection.setDeleted(false);
        inspection.setUpdatedBy(member.userId());
        if (existing == null) {
            inspectionMapper.insert(inspection);
        } else {
            inspectionMapper.updateById(inspection);
        }
        saveItemResults(inspection.getId(), submission.snapshots());
        bindMedia(inspection.getId(), request.getMediaFileIds(), member.userId());
        return InspectionConvert.submitResult(inspection, pen.penName());
    }

    @Override
    @Transactional
    public InspectionSubmitResultVO modify(Long inspectionId, InspectionSubmitRequest request) {
        requireRequest(request);
        Inspection inspection = requireInspection(inspectionId);
        FarmContext.FarmMember member = support.member();
        FarmContext.checkPen(inspection.getPenId());
        if (!InspectRules.DONE.equals(inspection.getInspectStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这条巡检还没有登记，请先登记");
        }
        if (member.isStaff()) {
            if (!Objects.equals(inspection.getRegistrantId(), member.userId())
                    || !BeijingTime.today().equals(inspection.getInspectDate())) {
                throw new BizException(ErrorCode.ROLE_FORBIDDEN, "只能修改自己当天登记的巡检");
            }
        } else if (!InspectRules.withinEditWindow(inspection.getInspectDate())) {
            throw new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED,
                    "这条记录已超过 3 天，修改需要提交修改申请");
        }
        if (inspection.getReportId() != null && request.getVersion() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请带上版本号");
        }
        if (request.getVersion() != null && !request.getVersion().equals(inspection.getVersion())) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        InspectTemplateBundle template;
        if (inspection.getTemplateId() != null && inspection.getTemplateVersion() != null) {
            template = support.templateForVersion(inspection.getTemplateId(), inspection.getTemplateVersion());
        } else {
            template = support.templateForCategory(categoryOfPen(inspection.getPenId()));
        }
        Boolean manualMarked = request.getIsManualMarked();
        String manualReason = request.getManualReason();
        if (member.isStaff()) {
            manualMarked = inspection.getIsManualMarked();
            manualReason = inspection.getManualReason();
        }
        SubmitInput input = new SubmitInput(request.getItemResults(), manualMarked, manualReason,
                request.getRemark(), inspection.getExecutorId(), null, request.getMediaFileIds());
        Submission submission = evaluate(input, template, true);
        if (inspection.getReportId() != null && !submission.abnormal()
                && !Boolean.TRUE.equals(manualMarked)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已转上报，不能取消异常标记");
        }
        inspection.setIsAbnormal(submission.abnormal());
        inspection.setAbnormalSource(submission.abnormalSource());
        inspection.setAbnormalSummary(submission.abnormalSummary());
        if (member.isManagerView() && !Objects.equals(manualMarked, inspection.getIsManualMarked())) {
            if (Boolean.TRUE.equals(manualMarked)) {
                inspection.setManualMarkedBy(member.userId());
                inspection.setManualMarkedAt(BeijingTime.now());
            } else {
                inspection.setManualMarkedBy(null);
                inspection.setManualMarkedAt(null);
            }
        }
        inspection.setIsManualMarked(Boolean.TRUE.equals(manualMarked));
        inspection.setManualReason(Boolean.TRUE.equals(manualMarked) ? blankToNull(manualReason) : null);
        inspection.setRemark(blankToNull(request.getRemark()));
        if (member.isManagerView() && request.getExecutorId() != null) {
            inspection.setExecutorId(resolveExecutor(request.getExecutorId(), member));
        }
        inspection.setStockSnapshot(totalStockOf(inspection.getPenId()));
        inspection.setUpdatedBy(member.userId());
        inspectionMapper.updateById(inspection);
        saveItemResults(inspection.getId(), submission.snapshots());
        if (request.getMediaFileIds() != null) {
            bindMedia(inspection.getId(), request.getMediaFileIds(), member.userId());
        }
        return InspectionConvert.submitResult(inspection, support.penName(inspection.getPenId()));
    }

    // ===== API-I09 补录 =====

    @Override
    @Transactional
    public InspectionSubmitResultVO backfill(InspectionBackfillRequest request) {
        if (request == null || request.getPenId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择栏舍");
        }
        support.requireManager("只有所有者和场长可以补录");
        FarmContext.FarmMember member = support.member();
        PenInfo pen = support.requirePen(request.getPenId());
        LocalDate date = InspectRules.parseDate(request.getInspectDate(), null);
        if (date == null || !InspectRules.inBackfillWindow(date)) {
            throw new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED, "只能补录前 3 天内的漏巡");
        }
        if (request.getClientRequestId() == null || request.getClientRequestId().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求编号不能为空");
        }
        Inspection byRequest = inspectionMapper.selectByClientRequestId(support.farmId(),
                request.getClientRequestId());
        if (byRequest != null) {
            return InspectionConvert.submitResult(byRequest, support.penName(byRequest.getPenId()));
        }
        if (request.getExecutorId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择实际执行人");
        }
        if (!support.memberMap().containsKey(request.getExecutorId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择本场成员作为实际执行人");
        }
        Inspection existing = inspectionMapper.selectByPenDate(support.farmId(), request.getPenId(), date);
        if (existing != null && InspectRules.DONE.equals(existing.getInspectStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED,
                    date.getMonthValue() + "月" + date.getDayOfMonth() + "日 已有巡检记录，可在巡检历史中修改");
        }
        List<BatchInfo> batches = support.batchesOfPen(request.getPenId());
        if (existing == null && batches.isEmpty()) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这一天不需要巡检");
        }
        BatchInfo primary = uniqueBatch(batches);
        InspectTemplateBundle template;
        if (existing != null && existing.getTemplateId() != null && existing.getTemplateVersion() != null) {
            template = support.templateForVersion(existing.getTemplateId(), existing.getTemplateVersion());
        } else {
            template = support.templateForCategory(primary != null ? primary.categoryId()
                    : batches.get(0).categoryId());
        }
        SubmitInput input = new SubmitInput(request.getItemResults(), request.getIsManualMarked(),
                request.getManualReason(), request.getRemark(), request.getExecutorId(), request.getLocation(),
                request.getMediaFileIds());
        Submission submission = evaluate(input, template, false);
        Inspection inspection = existing != null ? existing : new Inspection();
        inspection.setFarmId(support.farmId());
        inspection.setPenId(request.getPenId());
        if (primary != null) {
            inspection.setBatchId(primary.batchId());
        }
        inspection.setInspectDate(date);
        inspection.setInspectStatus(InspectRules.DONE);
        inspection.setTemplateId(template.templateId);
        inspection.setTemplateVersion(template.version);
        inspection.setStockSnapshot(totalStock(batches, request.getPenId()));
        inspection.setRegistrantId(member.userId());
        inspection.setExecutorId(request.getExecutorId());
        inspection.setRegisteredAt(BeijingTime.now());
        inspection.setClientRequestId(request.getClientRequestId());
        inspection.setDeadlineTime(support.deadline());
        inspection.setIsOverdue(false);
        inspection.setIsBackfill(true);
        applyLocation(inspection, request.getLocation());
        applySubmission(inspection, submission, member);
        inspection.setVersion(existing == null ? 0 : existing.getVersion());
        inspection.setDeleted(false);
        inspection.setUpdatedBy(member.userId());
        if (existing == null) {
            inspectionMapper.insert(inspection);
        } else {
            inspectionMapper.updateById(inspection);
        }
        saveItemResults(inspection.getId(), submission.snapshots());
        bindMedia(inspection.getId(), request.getMediaFileIds(), member.userId());
        return InspectionConvert.submitResult(inspection, pen.penName());
    }

    @Override
    public InspectionSubmitResultVO findByRequest(String clientRequestId) {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Inspection inspection = inspectionMapper.selectByClientRequestId(support.farmId(), clientRequestId);
        if (inspection == null || !Objects.equals(inspection.getRegistrantId(), support.userId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return InspectionConvert.submitResult(inspection, support.penName(inspection.getPenId()));
    }

    // ===== API-I10 详情 =====

    @Override
    public InspectionDetailVO detail(Long inspectionId) {
        Inspection inspection = requireInspection(inspectionId);
        return detailOf(inspection, true);
    }

    private InspectionDetailVO detailOf(Inspection inspection, boolean withEditMode) {
        FarmContext.checkPen(inspection.getPenId());
        FarmContext.FarmMember member = support.member();
        List<InspectionItemResult> results = itemResultMapper.selectByInspectionId(inspection.getId());
        List<MediaItemVO> media = support.media(BizType.INSPECTION, inspection.getId());
        List<String> batchNos = batchNosOf(inspection);
        InspectionDetailVO vo = InspectionConvert.detail(inspection, support.penName(inspection.getPenId()),
                batchNos, results, media, support.memberMap(), support.reportStatus(inspection.getReportId()));
        if (withEditMode) {
            boolean canDirect = InspectRules.DONE.equals(inspection.getInspectStatus())
                    && (member.isManagerView()
                    ? InspectRules.withinEditWindow(inspection.getInspectDate())
                    : Objects.equals(inspection.getRegistrantId(), member.userId())
                    && BeijingTime.today().equals(inspection.getInspectDate()));
            String editMode;
            if (canDirect) {
                editMode = "DIRECT";
            } else if (InspectRules.DONE.equals(inspection.getInspectStatus()) && member.isManagerView()) {
                editMode = "CHANGE_REQUEST";
            } else {
                editMode = "NONE";
            }
            InspectionConvert.withEditMode(vo, editMode, canDirect, false, null);
        }
        return vo;
    }

    // ===== API-I11 巡检历史 =====

    @Override
    public PageVO<PenInspectionRowVO> penHistory(Long penId, PenHistoryQuery query) {
        support.requirePen(penId);
        LocalDate today = BeijingTime.today();
        LocalDate start = InspectRules.parseDate(query == null ? null : query.getStartDate(),
                today.minusDays(6));
        LocalDate end = InspectRules.parseDate(query == null ? null : query.getEndDate(), today);
        if (start.isAfter(end)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开始日期不能晚于结束日期");
        }
        if (start.plusDays(92).isBefore(end)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "查询日期跨度不能超过 92 天");
        }
        int size = size(query == null ? null : query.getSize());
        int page = page(query == null ? null : query.getPage());
        String filter = query == null || query.getFilter() == null || query.getFilter().isBlank()
                ? "ALL" : query.getFilter().toUpperCase();
        List<Inspection> inspections = inspectionMapper.selectRange(support.farmId(), penId, start, end);
        FarmContext.FarmMember member = support.member();
        Map<Long, FarmMemberRow> members = support.memberMap();
        List<PenInspectionRowVO> rows = new ArrayList<>();
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("ALL", 0);
        tabs.put("ABNORMAL", 0);
        tabs.put("OVERDUE", 0);
        tabs.put("BACKFILL", 0);
        tabs.put("OFF_SITE", 0);
        if (inspections != null) {
            for (Inspection inspection : inspections) {
                if (!InspectRules.DONE.equals(inspection.getInspectStatus())
                        && !InspectRules.MISSED.equals(inspection.getInspectStatus())) {
                    continue;
                }
                boolean abnormal = Boolean.TRUE.equals(inspection.getIsAbnormal());
                boolean overdue = Boolean.TRUE.equals(inspection.getIsOverdue());
                boolean backfill = Boolean.TRUE.equals(inspection.getIsBackfill());
                boolean offSite = "OFF_SITE".equals(inspection.getLocationStatus());
                tabs.merge("ALL", 1, Integer::sum);
                if (abnormal) {
                    tabs.merge("ABNORMAL", 1, Integer::sum);
                }
                if (overdue) {
                    tabs.merge("OVERDUE", 1, Integer::sum);
                }
                if (backfill) {
                    tabs.merge("BACKFILL", 1, Integer::sum);
                }
                if (offSite) {
                    tabs.merge("OFF_SITE", 1, Integer::sum);
                }
                boolean include = switch (filter) {
                    case "ABNORMAL" -> abnormal;
                    case "OVERDUE" -> overdue;
                    case "BACKFILL" -> backfill;
                    case "OFF_SITE" -> offSite;
                    default -> true;
                };
                if (!include) {
                    continue;
                }
                boolean canModify = InspectRules.DONE.equals(inspection.getInspectStatus())
                        && (member.isManagerView()
                        ? InspectRules.withinEditWindow(inspection.getInspectDate())
                        : Objects.equals(inspection.getRegistrantId(), member.userId())
                        && today.equals(inspection.getInspectDate()));
                boolean canChangeRequest = InspectRules.DONE.equals(inspection.getInspectStatus())
                        && member.isManagerView() && !InspectRules.withinEditWindow(inspection.getInspectDate());
                boolean canBackfill = InspectRules.MISSED.equals(inspection.getInspectStatus())
                        && member.isManagerView() && InspectRules.inBackfillWindow(inspection.getInspectDate());
                FarmMemberRow registrant = inspection.getRegistrantId() == null ? null
                        : members.get(inspection.getRegistrantId());
                String summary = abnormal ? inspection.getAbnormalSummary() : "正常";
                rows.add(InspectionConvert.penRow(inspection, today.equals(inspection.getInspectDate()),
                        registrant == null ? null : registrant.getName(), canModify, canChangeRequest,
                        canBackfill, summary));
            }
        }
        int targetPage = page;
        String locateDate = query == null ? null : query.getLocateDate();
        if (locateDate != null && !locateDate.isBlank()) {
            for (int i = 0; i < rows.size(); i++) {
                if (locateDate.equals(rows.get(i).getInspectDate())) {
                    targetPage = i / size + 1;
                    break;
                }
            }
        }
        int total = rows.size();
        int from = Math.min((targetPage - 1) * size, total);
        int to = Math.min(from + size, total);
        return PageVO.of(new ArrayList<>(rows.subList(from, to)), total, targetPage, size)
                .withTabCounts(tabs);
    }

    // ===== API-I12 近 7 天概况 =====

    @Override
    public PenInspectionOverviewVO penOverview(Long penId, String to) {
        PenInfo pen = support.requirePen(penId);
        LocalDate end = InspectRules.parseDate(to, BeijingTime.today());
        LocalDate start = end.minusDays(6);
        List<Inspection> inspections = inspectionMapper.selectRange(support.farmId(), penId, start, end);
        Map<LocalDate, Inspection> byDate = inspections == null ? Map.of()
                : inspections.stream().filter(i -> !InspectRules.CANCELLED.equals(i.getInspectStatus()))
                .collect(Collectors.toMap(Inspection::getInspectDate, Function.identity(), (a, b) -> a));
        PenInspectionOverviewVO vo = new PenInspectionOverviewVO();
        vo.setPenId(penId);
        vo.setPenName(pen.penName());
        List<String> batchNos = new ArrayList<>();
        for (BatchInfo batch : support.batchesOfPen(penId)) {
            batchNos.add(batch.batchNo());
        }
        vo.setBatchNos(batchNos);
        vo.setResponsibleNames(namesOfPen(penId));
        vo.setToday(BeijingTime.format(BeijingTime.today()));
        vo.setBackfillFrom(BeijingTime.format(BeijingTime.today().minusDays(InspectRules.BACKFILL_DAYS)));
        vo.setBackfillTo(BeijingTime.format(BeijingTime.today().minusDays(1)));
        List<PenInspectionOverviewVO.DayCell> days = new ArrayList<>();
        int done = 0;
        LocalDate today = BeijingTime.today();
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            Inspection inspection = byDate.get(date);
            PenInspectionOverviewVO.DayCell cell = new PenInspectionOverviewVO.DayCell();
            cell.setDate(BeijingTime.format(date));
            String tag;
            if (inspection != null) {
                if (InspectRules.DONE.equals(inspection.getInspectStatus())) {
                    done++;
                }
                if (Boolean.TRUE.equals(inspection.getIsAbnormal())) {
                    tag = "ABNORMAL";
                    cell.setAbnormalSource(inspection.getAbnormalSource());
                } else if (Boolean.TRUE.equals(inspection.getIsBackfill())) {
                    tag = "BACKFILL";
                } else if (Boolean.TRUE.equals(inspection.getIsOverdue())) {
                    tag = "OVERDUE";
                } else if (InspectRules.MISSED.equals(inspection.getInspectStatus())) {
                    tag = "MISSED";
                } else if (InspectRules.PENDING.equals(inspection.getInspectStatus())) {
                    tag = "PENDING";
                } else {
                    tag = "DONE";
                }
                cell.setInspectionId(inspection.getId());
            } else if (date.isBefore(today)) {
                tag = "MISSED";
            } else if (date.equals(today)) {
                tag = "PENDING";
            } else {
                tag = "NONE";
            }
            cell.setDayTag(tag);
            days.add(cell);
        }
        vo.setRecent7DoneDays(done);
        vo.setRecent7ShouldDays(7);
        vo.setDays(days);
        return vo;
    }

    // ===== 共用 =====

    private void requireRequest(InspectionSubmitRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求内容不能为空");
        }
    }

    private void requireNotFuture(LocalDate date) {
        if (date.isAfter(BeijingTime.today())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能查看今天及以前的巡检");
        }
    }

    private Inspection requireInspection(Long inspectionId) {
        if (inspectionId == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Inspection inspection = inspectionMapper.selectById(inspectionId);
        if (inspection == null || !support.farmId().equals(inspection.getFarmId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return inspection;
    }

    private List<RowData> dayRows(LocalDate date) {
        Map<Long, Inspection> inspections = new LinkedHashMap<>();
        List<Inspection> day = inspectionMapper.selectDay(support.farmId(), date);
        if (day != null) {
            for (Inspection inspection : day) {
                if (!InspectRules.CANCELLED.equals(inspection.getInspectStatus())) {
                    inspections.put(inspection.getPenId(), inspection);
                }
            }
        }
        Map<Long, List<BatchInfo>> batches = new LinkedHashMap<>();
        for (BatchInfo batch : support.activeBatches()) {
            if (batch.penIds() == null) {
                continue;
            }
            for (Long penId : batch.penIds()) {
                batches.computeIfAbsent(penId, id -> new ArrayList<>()).add(batch);
            }
        }
        Map<Long, Integer> stock = new LinkedHashMap<>();
        for (BatchPenStockRow row : support.activeBatchPens()) {
            stock.merge(row.getPenId(), row.getStockCount() == null ? 0 : row.getStockCount(), Integer::sum);
        }
        Map<Long, List<String>> responsibles = support.penResponsibles().stream()
                .collect(Collectors.groupingBy(PenResponsibleRow::getPenId,
                        Collectors.mapping(PenResponsibleRow::getName, Collectors.toList())));
        LocalDate today = BeijingTime.today();
        List<RowData> rows = new ArrayList<>();
        for (PenInfo pen : support.pens()) {
            Inspection inspection = inspections.get(pen.penId());
            int penStock = stock.getOrDefault(pen.penId(), 0);
            if (penStock <= 0 && inspection == null) {
                continue;
            }
            if (penStock <= 0 && inspection != null && !InspectRules.DONE.equals(inspection.getInspectStatus())) {
                continue;
            }
            String display = inspection != null ? InspectRules.displayStatus(inspection)
                    : (date.isBefore(today) ? "MISSED" : "PENDING");
            if (date.isAfter(today)) {
                display = "PENDING";
            }
            List<BatchInfo> penBatches = batches.getOrDefault(pen.penId(), List.of());
            List<String> batchNos = penBatches.stream().map(BatchInfo::batchNo).toList();
            rows.add(new RowData(pen, inspection, penBatches, penStock, display,
                    responsibles.getOrDefault(pen.penId(), List.of())));
        }
        rows.sort(Comparator
                .comparingInt((RowData row) -> switch (row.displayStatus()) {
                    case "ABNORMAL" -> 0;
                    case "PENDING" -> 1;
                    case "DONE" -> 2;
                    default -> 3;
                })
                .thenComparing(row -> switch (row.displayStatus()) {
                    case "ABNORMAL", "DONE", "MISSED" -> row.inspection() == null
                            || row.inspection().getRegisteredAt() == null ? "" :
                            BeijingTime.format(row.inspection().getRegisteredAt());
                    default -> row.pen().penCode();
                }));
        return rows;
    }

    private InspectionListItemVO listItem(RowData row, LocalDate date) {
        FarmContext.FarmMember member = support.member();
        boolean canModify = row.inspection() != null && InspectRules.DONE.equals(row.inspection().getInspectStatus())
                && (member.isManagerView()
                ? InspectRules.withinEditWindow(row.inspection().getInspectDate())
                : Objects.equals(row.inspection().getRegistrantId(), member.userId())
                && BeijingTime.today().equals(row.inspection().getInspectDate()));
        FarmMemberRow registrant = row.inspection() == null || row.inspection().getRegistrantId() == null ? null
                : support.memberMap().get(row.inspection().getRegistrantId());
        String reportStatus = row.inspection() == null ? null : support.reportStatus(row.inspection().getReportId());
        boolean canReport = row.inspection() != null && canReport(row.inspection());
        return InspectionConvert.listItem(row.inspection(), row.pen().penId(), row.pen().penCode(),
                row.pen().penName(),
                row.inspection() == null || row.inspection().getBatchId() == null
                        ? (row.batches().size() == 1 ? row.batches().get(0).batchId() : null)
                        : row.inspection().getBatchId(),
                row.batches().stream().map(BatchInfo::batchNo).toList(),
                row.stock(), row.responsibleNames(),
                row.displayStatus(), registrant == null ? null : registrant.getName(), reportStatus,
                canModify, canReport,
                InspectRules.deadlinePassed(date, support.deadline(), BeijingTime.now()));
    }

    private boolean canReport(Inspection inspection) {
        return InspectRules.DONE.equals(inspection.getInspectStatus())
                && Boolean.TRUE.equals(inspection.getIsAbnormal())
                && !Boolean.TRUE.equals(inspection.getIsBackfill())
                && BeijingTime.today().equals(inspection.getInspectDate())
                && inspection.getReportId() == null;
    }

    private Submission evaluate(SubmitInput input, InspectTemplateBundle template, boolean modifying) {
        List<InspectTemplateBundle.ResultSnapshot> snapshots = template.validate(input.itemResults());
        FarmContext.FarmMember member = support.member();
        boolean checked = snapshots.stream().anyMatch(InspectTemplateBundle.ResultSnapshot::optionAbnormal);
        boolean manualRequested = Boolean.TRUE.equals(input.manualMarked());
        if (manualRequested && !member.isManagerView()) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "只有所有者和场长可以手动标记异常");
        }
        String reason = blankToNull(input.manualReason());
        if (!checked && manualRequested && reason == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写异常原因");
        }
        if (reason != null && reason.length() > 100) {
            throw new BizException(ErrorCode.PARAM_INVALID, "异常原因不能超过 100 字");
        }
        if (input.remark() != null && input.remark().length() > 200) {
            throw new BizException(ErrorCode.PARAM_INVALID, "说明不能超过 200 字");
        }
        boolean abnormal = checked || manualRequested;
        String source = checked ? "CHECK" : (manualRequested ? "MANUAL" : null);
        String summary = checked ? InspectRules.summaryOf(
                InspectTemplateBundle.abnormalSummaries(snapshots)) : null;
        return new Submission(snapshots, abnormal, source, summary, manualRequested, reason);
    }

    private void applySubmission(Inspection inspection, Submission submission, FarmContext.FarmMember member) {
        inspection.setIsAbnormal(submission.abnormal());
        inspection.setAbnormalSource(submission.abnormalSource());
        inspection.setAbnormalSummary(submission.abnormalSummary());
        inspection.setIsManualMarked(submission.manualMarked());
        if (submission.manualMarked() && !Objects.equals(submission.manualReason(), inspection.getManualReason())) {
            inspection.setManualMarkedBy(member.userId());
            inspection.setManualMarkedAt(BeijingTime.now());
        }
        inspection.setManualReason(submission.manualMarked() ? submission.manualReason() : null);
    }

    private void applyLocation(Inspection inspection, InspectionSubmitRequest.LocationRequest location) {
        FarmGeoRow farm = support.farmGeo();
        String clientStatus = location == null ? null : location.getClientStatus();
        BigDecimal latitude = location == null || location.getLatitude() == null ? null
                : BigDecimal.valueOf(location.getLatitude());
        BigDecimal longitude = location == null || location.getLongitude() == null ? null
                : BigDecimal.valueOf(location.getLongitude());
        GeoDistance.Result result = GeoDistance.evaluate(clientStatus, latitude, longitude,
                farm == null ? null : farm.getLatitude(), farm == null ? null : farm.getLongitude());
        inspection.setLocationStatus(result.locationStatus());
        inspection.setDistanceM(result.distanceM());
        inspection.setLatitude(latitude);
        inspection.setLongitude(longitude);
        inspection.setLocationAccuracyM(location == null || location.getAccuracyM() == null ? null
                : (int) Math.round(location.getAccuracyM()));
    }

    private Long resolveExecutor(Long requested, FarmContext.FarmMember member) {
        if (!member.isManagerView()) {
            return member.userId();
        }
        Long executor = requested == null ? member.userId() : requested;
        if (!support.memberMap().containsKey(executor)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择本场成员作为实际执行人");
        }
        return executor;
    }

    private void saveItemResults(Long inspectionId, List<InspectTemplateBundle.ResultSnapshot> snapshots) {
        itemResultMapper.deleteByInspectionId(inspectionId);
        for (InspectTemplateBundle.ResultSnapshot snapshot : snapshots) {
            InspectionItemResult result = new InspectionItemResult();
            result.setFarmId(support.farmId());
            result.setInspectionId(inspectionId);
            result.setItemId(snapshot.itemId());
            result.setItemName(snapshot.itemName());
            result.setOptionId(snapshot.optionId());
            result.setOptionName(snapshot.optionName());
            result.setOptionAbnormal(snapshot.optionAbnormal());
            result.setSortNo(snapshot.sortNo());
            itemResultMapper.insert(result);
        }
    }

    private void bindMedia(Long inspectionId, List<Long> fileIds, Long userId) {
        if (fileIds == null) {
            return;
        }
        List<Long> existing = bizMediaService.listFileIds(BizType.INSPECTION, inspectionId);
        List<Long> fresh = new ArrayList<>();
        for (Long fileId : fileIds) {
            if (!existing.contains(fileId)) {
                fresh.add(fileId);
            }
        }
        bizMediaService.validateForBind(fresh, FileScene.INSPECTION, userId, "media_file_ids",
                new BizRef(BizType.INSPECTION, inspectionId));
        bizMediaService.replace(BizType.INSPECTION, inspectionId, support.farmId(), fileIds);
    }

    private List<String> namesOfPen(Long penId) {
        List<String> names = new ArrayList<>();
        for (PenResponsibleRow row : support.penResponsibles()) {
            if (row.getPenId() != null && row.getPenId().equals(penId) && row.getName() != null) {
                names.add(row.getName());
            }
        }
        return names;
    }

    private List<OptionVO> memberOptions() {
        List<OptionVO> options = new ArrayList<>();
        for (FarmMemberRow row : support.memberList()) {
            options.add(OptionVO.builder().value(String.valueOf(row.getUserId())).label(row.getName())
                    .hint(InspectionConvert.roleText(row.getRole())).disabled(false).build());
        }
        return options;
    }

    private InspectionFormVO.InspectTemplate templateVo(InspectTemplateBundle bundle) {
        InspectionFormVO.InspectTemplate template = new InspectionFormVO.InspectTemplate();
        template.setTemplateId(bundle.templateId);
        template.setTemplateVersion(bundle.version);
        List<InspectionFormVO.TemplateItem> items = new ArrayList<>();
        for (InspectTemplateBundle.Item item : bundle.items) {
            InspectionFormVO.TemplateItem vo = new InspectionFormVO.TemplateItem();
            vo.setItemId(item.itemId());
            vo.setItemName(item.itemName());
            vo.setSortNo(item.sortNo());
            List<InspectionFormVO.TemplateOption> options = new ArrayList<>();
            for (InspectTemplateBundle.Option option : item.options()) {
                InspectionFormVO.TemplateOption optionVo = new InspectionFormVO.TemplateOption();
                optionVo.setOptionId(option.optionId());
                optionVo.setOptionName(option.optionName());
                optionVo.setIsAbnormal(option.abnormal());
                optionVo.setSortNo(option.sortNo());
                options.add(optionVo);
            }
            options.sort(Comparator.comparingInt(InspectionFormVO.TemplateOption::getSortNo));
            vo.setOptions(options);
            items.add(vo);
        }
        items.sort(Comparator.comparingInt(InspectionFormVO.TemplateItem::getSortNo));
        template.setItems(items);
        return template;
    }

    private List<String> batchNosOf(Inspection inspection) {
        List<String> batchNos = new ArrayList<>();
        if (inspection.getBatchId() != null) {
            try {
                batchNos.add(support.requireActiveBatch(inspection.getBatchId()).batchNo());
            } catch (BizException ignored) {
                // 已结束批次：历史记录仍显示当前在栏批次号
            }
        }
        if (batchNos.isEmpty()) {
            for (BatchInfo batch : support.batchesOfPen(inspection.getPenId())) {
                batchNos.add(batch.batchNo());
            }
        }
        return batchNos;
    }

    private List<String> backfillDates(Long penId) {
        LocalDate today = BeijingTime.today();
        List<Inspection> rows = inspectionMapper.selectRange(support.farmId(), penId,
                today.minusDays(InspectRules.BACKFILL_DAYS), today.minusDays(1));
        List<String> dates = new ArrayList<>();
        if (rows != null) {
            for (Inspection inspection : rows) {
                if (InspectRules.MISSED.equals(inspection.getInspectStatus())) {
                    dates.add(BeijingTime.format(inspection.getInspectDate()));
                }
            }
        }
        return dates;
    }

    private LocalDate defaultBackfillDate(Long penId) {
        List<String> dates = backfillDates(penId);
        return dates.isEmpty() ? BeijingTime.today().minusDays(1) : LocalDate.parse(dates.get(0));
    }

    private Long categoryOfPen(Long penId) {
        List<BatchInfo> batches = support.batchesOfPen(penId);
        return batches.isEmpty() ? null : batches.get(0).categoryId();
    }

    private int totalStock(List<BatchInfo> batches, Long penId) {
        int total = 0;
        for (BatchInfo batch : batches) {
            total += support.batchPenStock(batch.batchId(), penId);
        }
        return total;
    }

    private int totalStockOf(Long penId) {
        return totalStock(support.batchesOfPen(penId), penId);
    }

    private static BatchInfo uniqueBatch(List<BatchInfo> batches) {
        return batches.size() == 1 ? batches.get(0) : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Double doubleValue(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static int page(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private static int size(Integer size) {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private record RowData(PenInfo pen, Inspection inspection, List<BatchInfo> batches, int stock,
                           String displayStatus, List<String> responsibleNames) {
    }

    private record SubmitInput(List<InspectionSubmitRequest.ItemResultRequest> itemResults,
                               Boolean manualMarked, String manualReason, String remark, Long executorId,
                               InspectionSubmitRequest.LocationRequest location, List<Long> mediaFileIds) {
    }

    private record Submission(List<InspectTemplateBundle.ResultSnapshot> snapshots, boolean abnormal,
                              String abnormalSource, String abnormalSummary, boolean manualMarked,
                              String manualReason) {
    }
}
