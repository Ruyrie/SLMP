package com.zhimu.server.biz.health.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.base.api.DrugCatalogInfo;
import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.biz.health.entity.ImmunePlan;
import com.zhimu.server.biz.health.entity.ImmuneRecord;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.mapper.ImmunePlanMapper;
import com.zhimu.server.biz.health.mapper.ImmuneRecordMapper;
import com.zhimu.server.biz.health.query.FarmDrugRow;
import com.zhimu.server.biz.health.query.ImmunePlanOptionQuery;
import com.zhimu.server.biz.health.query.ImmunePlanQuery;
import com.zhimu.server.biz.health.query.DrugStockRow;
import com.zhimu.server.biz.health.request.ImmunePlanCreateRequest;
import com.zhimu.server.biz.health.request.ImmunePlanPostponeRequest;
import com.zhimu.server.biz.health.request.ImmunePlanUpdateRequest;
import com.zhimu.server.biz.health.service.ImmunePlanService;
import com.zhimu.server.biz.health.support.HealthAudit;
import com.zhimu.server.biz.health.support.HealthCodes;
import com.zhimu.server.biz.health.support.HealthFormat;
import com.zhimu.server.biz.health.support.HealthSupport;
import com.zhimu.server.biz.health.vo.DrugLotVO;
import com.zhimu.server.biz.health.vo.ImmunePlanOptionVO;
import com.zhimu.server.biz.health.vo.ImmunePlanOverviewVO;
import com.zhimu.server.biz.health.vo.ImmunePlanVO;
import com.zhimu.server.biz.health.vo.ImmuneRegisterFormVO;
import com.zhimu.server.biz.health.vo.VaccineOptionVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.stock.api.DrugLotOption;
import com.zhimu.server.biz.stock.api.DrugStockPort;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API-D01–D13 免疫计划实现。 */
@Service
@RequiredArgsConstructor
public class ImmunePlanServiceImpl implements ImmunePlanService {

    private static final LocalTime DEFAULT_DUE_TIME = LocalTime.of(18, 0);
    private static final String REMIND_TEXT = "前 1 天和当天各提醒一次";
    private static final List<String> LIST_STATUSES = List.of(HealthCodes.PLAN_PENDING, HealthCodes.PLAN_POSTPONED,
            HealthCodes.PLAN_MISSED, HealthCodes.PLAN_DONE, HealthCodes.PLAN_CANCELLED);

    private final ImmunePlanMapper planMapper;
    private final ImmuneRecordMapper recordMapper;
    private final HealthReferenceMapper referenceMapper;
    private final ImmunePlanGenerator generator;
    private final HealthSupport support;
    private final HealthAudit audit;
    private final DrugStockPort drugStockPort;
    private final DrugCatalogQueryService drugCatalogQueryService;
    private final HealthTodoSource todoSource;

    // ===== API-D01 概况 =====

    @Override
    public ImmunePlanOverviewVO overview(Long batchId) {
        BatchInfo batch = support.requireBatch(batchId);
        support.checkBatchAccess(batch);
        generator.ensureGenerated(batch);
        todoSource.generate(com.zhimu.server.biz.today.api.TodoSource.TodoSourceContext.builder()
                .farmId(batch.farmId()).build());
        List<ImmunePlan> plans = planMapper.selectList(Wrappers.<ImmunePlan>lambdaQuery()
                .eq(ImmunePlan::getBatchId, batchId)
                .orderByAsc(ImmunePlan::getPlanDate).orderByAsc(ImmunePlan::getId));
        ImmunePlanOverviewVO vo = new ImmunePlanOverviewVO();
        vo.setBatchId(batch.batchId());
        vo.setBatchNo(batch.batchNo());
        Long mainPenId = support.mainPenId(batch);
        vo.setPenId(mainPenId);
        vo.setPenName(support.penName(mainPenId));
        vo.setBreedName(batch.breedId() == null ? null : referenceMapper.selectBreedName(batch.breedId()));
        vo.setCategoryId(batch.categoryId());
        vo.setEntryDate(batch.entryDate());
        int entryAge = batch.entryAgeDays() == null ? 0 : batch.entryAgeDays();
        vo.setEntryAgeDays(entryAge);
        vo.setDayAge(dayAge(batch, entryAge));
        vo.setCurrentCount(batch.currentCount() == null ? 0 : batch.currentCount());
        vo.setBatchEnded(!batch.active());
        vo.setHasTemplate(generator.hasTemplate(batch.breedId()));
        vo.setCanManage(support.managerView() && batch.active());

        ImmunePlanOverviewVO.Counts counts = new ImmunePlanOverviewVO.Counts();
        int all = 0;
        int pending = 0;
        int missed = 0;
        int postponed = 0;
        int done = 0;
        int cancelled = 0;
        List<ImmunePlanOverviewVO.PreEntryItem> preEntry = new ArrayList<>();
        for (ImmunePlan plan : plans) {
            String status = plan.getStatus();
            if (HealthCodes.PLAN_PRE_ENTRY.equals(status)) {
                ImmunePlanOverviewVO.PreEntryItem item = new ImmunePlanOverviewVO.PreEntryItem();
                item.setPlanId(plan.getId());
                item.setVaccineName(plan.getVaccineName());
                item.setImmuneRound(plan.getImmuneRound());
                item.setTemplateAgeDay(plan.getPlanAgeDay());
                item.setVersion(plan.getVersion());
                preEntry.add(item);
                continue;
            }
            if (HealthCodes.PLAN_PRE_ENTRY_DONE.equals(status)) {
                continue;
            }
            all++;
            switch (status) {
                case HealthCodes.PLAN_PENDING -> pending++;
                case HealthCodes.PLAN_MISSED -> missed++;
                case HealthCodes.PLAN_POSTPONED -> postponed++;
                case HealthCodes.PLAN_DONE -> done++;
                case HealthCodes.PLAN_CANCELLED -> cancelled++;
                default -> {
                }
            }
        }
        counts.setAll(all);
        counts.setPending(pending);
        counts.setMissed(missed);
        counts.setPostponed(postponed);
        counts.setDone(done);
        counts.setCancelled(cancelled);
        vo.setCounts(counts);
        vo.setPreEntryItems(preEntry);
        vo.setPreEntryPendingCount(preEntry.size());
        return vo;
    }

    // ===== API-D02 列表 =====

    @Override
    public PageVO<ImmunePlanVO> list(Long batchId, ImmunePlanQuery query) {
        BatchInfo batch = support.requireBatch(batchId);
        support.checkBatchAccess(batch);
        generator.ensureGenerated(batch);
        todoSource.generate(com.zhimu.server.biz.today.api.TodoSource.TodoSourceContext.builder()
                .farmId(batch.farmId()).build());
        int pageNo = query == null ? 1 : query.pageOrDefault();
        int size = query == null ? 20 : query.sizeOrDefault();
        String status = query == null ? null : query.getStatus();
        var wrapper = Wrappers.<ImmunePlan>lambdaQuery()
                .eq(ImmunePlan::getBatchId, batchId)
                .notIn(ImmunePlan::getStatus, HealthCodes.PLAN_PRE_ENTRY, HealthCodes.PLAN_PRE_ENTRY_DONE);
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(ImmunePlan::getStatus, status.toUpperCase());
        }
        wrapper.orderByAsc(ImmunePlan::getPlanDate).orderByAsc(ImmunePlan::getId);
        Page<ImmunePlan> result = planMapper.selectPage(new Page<>(pageNo, size), wrapper);
        Map<Long, String> names = support.memberNames();
        Long mainPenId = support.mainPenId(batch);
        List<ImmunePlanVO> list = new ArrayList<>();
        for (ImmunePlan plan : result.getRecords()) {
            list.add(toPlanVO(plan, batch, mainPenId, names));
        }
        return PageVO.of(list, result.getTotal(), pageNo, size);
    }

    // ===== API-D11 计划下拉 =====

    @Override
    public PageVO<ImmunePlanOptionVO> options(ImmunePlanOptionQuery query) {
        int pageNo = query == null ? 1 : query.pageOrDefault();
        int size = query == null ? 20 : query.sizeOrDefault();
        String keyword = query == null || query.getKeyword() == null ? null : query.getKeyword().trim();
        List<ImmunePlan> plans = planMapper.selectList(Wrappers.<ImmunePlan>lambdaQuery()
                .and(w -> w.in(ImmunePlan::getStatus, HealthCodes.PLAN_PENDING, HealthCodes.PLAN_POSTPONED,
                                HealthCodes.PLAN_MISSED)
                        .or(x -> x.eq(ImmunePlan::getStatus, HealthCodes.PLAN_DONE)
                                .eq(ImmunePlan::getManualDone, true))));
        FarmContext.FarmMember member = support.member();
        Map<Long, BatchInfo> batchCache = new HashMap<>();
        List<ImmunePlanOptionVO> all = new ArrayList<>();
        for (ImmunePlan plan : plans) {
            // 员工下拉只含负责栏舍的 PENDING、POSTPONED（漏免需所有者、场长补录）
            if (member.isStaff() && !List.of(HealthCodes.PLAN_PENDING, HealthCodes.PLAN_POSTPONED)
                    .contains(plan.getStatus())) {
                continue;
            }
            BatchInfo batch = plan.getBatchId() == null ? null : batchCache.computeIfAbsent(plan.getBatchId(),
                    support::requireBatch);
            Long penId = batch == null ? plan.getPenId() : support.mainPenId(batch);
            if (member.isStaff() && !member.canAccessPen(penId)) {
                continue;
            }
            String batchNo = batch == null ? null : batch.batchNo();
            if (keyword != null && !keyword.isEmpty()
                    && !contains(plan.getVaccineName(), keyword) && !contains(batchNo, keyword)) {
                continue;
            }
            ImmunePlanOptionVO vo = new ImmunePlanOptionVO();
            vo.setPlanId(plan.getId());
            vo.setTargetType(plan.getTargetType());
            vo.setBatchId(plan.getBatchId());
            vo.setBatchNo(batchNo);
            vo.setPenName(support.penName(penId));
            vo.setVaccineName(plan.getVaccineName());
            vo.setImmuneRound(plan.getImmuneRound());
            vo.setCurrentCount(batch != null ? (batch.currentCount() == null ? 0 : batch.currentCount())
                    : (plan.getPenId() == null ? 0
                    : referenceMapper.countBreedingAnimals(plan.getFarmId(), plan.getPenId())));
            vo.setPlanDate(plan.getPlanDate());
            vo.setPlanDateText(HealthFormat.dateText(plan.getPlanDate()));
            vo.setStatus(plan.getStatus());
            vo.setStatusText(HealthCodes.immuneStatusText(plan.getStatus()));
            all.add(vo);
        }
        all.sort(Comparator.comparing((ImmunePlanOptionVO vo) -> !HealthCodes.PLAN_MISSED.equals(vo.getStatus()))
                .thenComparing(ImmunePlanOptionVO::getPlanDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ImmunePlanOptionVO::getPlanId));
        int from = Math.min((pageNo - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageVO.of(new ArrayList<>(all.subList(from, to)), all.size(), pageNo, size);
    }

    // ===== API-D12 登记预填 =====

    @Override
    public ImmuneRegisterFormVO registerForm(Long planId, Boolean backfill) {
        ImmunePlan plan = requirePlan(planId);
        BatchInfo batch = plan.getBatchId() == null ? null : support.requireBatch(plan.getBatchId());
        if (batch != null) {
            support.checkBatchAccess(batch);
        } else {
            support.checkPenAccess(plan.getPenId());
        }
        boolean manualDone = Boolean.TRUE.equals(plan.getManualDone());
        boolean allowed = List.of(HealthCodes.PLAN_PENDING, HealthCodes.PLAN_POSTPONED, HealthCodes.PLAN_MISSED)
                .contains(plan.getStatus())
                || (HealthCodes.PLAN_DONE.equals(plan.getStatus()) && manualDone);
        if (!allowed) {
            ImmuneRecord record = plan.getRecordId() == null ? null : recordMapper.selectById(plan.getRecordId());
            String executor = record == null ? null : support.userName(record.getExecutorId());
            throw new BizException(ErrorCode.STATE_CHANGED,
                    "这项免疫已由" + (executor == null ? "他人" : executor) + "登记，无需重复登记");
        }
        boolean missed = HealthCodes.PLAN_MISSED.equals(plan.getStatus());
        boolean backfillFlag = missed || Boolean.TRUE.equals(backfill);
        if (support.staffView() && backfillFlag) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "漏免需所有者或场长补录");
        }
        int currentCount = currentCount(plan, batch);
        Long penId = batch == null ? plan.getPenId() : support.mainPenId(batch);
        FarmDrugRow drug = plan.getDrugId() == null ? null : drugById(plan.getDrugId());
        BigDecimal totalDose = plan.getDose() == null ? null
                : plan.getDose().multiply(BigDecimal.valueOf(Math.max(currentCount, 1)));

        ImmuneRegisterFormVO vo = new ImmuneRegisterFormVO();
        vo.setPlan(optionOf(plan, batch, penId));
        vo.setDrugId(plan.getDrugId());
        vo.setVaccineName(plan.getVaccineName());
        vo.setTemplateDose(plan.getDose());
        vo.setDose(plan.getDose());
        vo.setDoseUnit(HealthCodes.doseUnitText(plan.getDoseUnit()));
        vo.setQuantityDefault(currentCount);
        vo.setCurrentCount(currentCount);
        vo.setMethodCode(plan.getMethodCode());
        vo.setBackfill(backfillFlag);
        vo.setDateDefault(BeijingTime.today());
        vo.setDateMax(BeijingTime.today());
        vo.setDateMin(dateMin(plan, batch, backfillFlag));
        vo.setCreatorName(support.userName(UserContext.userId()));
        if (support.managerView()) {
            vo.setDefaultExecutorId(support.defaultExecutorId(penId));
        }
        if (drug == null) {
            vo.setNoLotText(noLotText(plan.getVaccineName()));
            return vo;
        }
        List<DrugLotOption> lots = lots(plan.getDrugId(), HealthCodes.PURPOSE_VACCINATE, totalDose);
        DrugLotOption first = firstSelectable(lots);
        if (first == null) {
            vo.setNoLotText(noLotText(plan.getVaccineName()));
        } else {
            vo.setDefaultLot(lotVO(first));
        }
        return vo;
    }

    // ===== API-D13 疫苗下拉 =====

    @Override
    public List<VaccineOptionVO> vaccineOptions(Long categoryId) {
        String species = support.species(categoryId);
        Map<Long, BigDecimal> stock = stockByDrug();
        Map<Long, FarmDrugRow> drugs = drugRows();
        List<VaccineOptionVO> list = new ArrayList<>();
        for (FarmDrugRow row : drugs.values()) {
            if (!HealthCodes.CATEGORY_VACCINE.equals(row.getCategory())) {
                continue;
            }
            DrugCatalogInfo catalog;
            try {
                catalog = drugCatalogQueryService.requireCatalog(row.getCatalogId());
            } catch (RuntimeException e) {
                continue;
            }
            if (drugCatalogQueryService.isBanned(row.getCatalogId(), species)) {
                continue;
            }
            BigDecimal qty = stock.getOrDefault(row.getId(), BigDecimal.ZERO);
            VaccineOptionVO vo = new VaccineOptionVO();
            vo.setDrugId(row.getId());
            vo.setCatalogId(row.getCatalogId());
            vo.setName(catalog.name());
            vo.setDoseUnit(HealthCodes.vaccineDoseUnitText(catalog.vaccineDoseUnit()));
            vo.setStockUnit(HealthCodes.unitText(row.getUnit()));
            vo.setAvailableQty(HealthFormat.plain(qty));
            vo.setNoStock(qty.signum() <= 0);
            vo.setDefaultMethodCode(catalog.defaultVaccinateMethodCode());
            list.add(vo);
        }
        return list;
    }

    // ===== API-D03 / D04 / D05 入栏前项目 =====

    @Override
    @Transactional
    public ImmunePlanOverviewVO preEntryConfirm(Long planId, Integer version) {
        support.requireManager("只有所有者和场长可以处理入栏前项目");
        ImmunePlan plan = requirePlan(planId);
        requireVersion(version);
        if (!HealthCodes.PLAN_PRE_ENTRY.equals(plan.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "该项目已处理，请刷新");
        }
        int rows = planMapper.update(null, Wrappers.<ImmunePlan>lambdaUpdate()
                .eq(ImmunePlan::getId, planId).eq(ImmunePlan::getVersion, version)
                .set(ImmunePlan::getStatus, HealthCodes.PLAN_PRE_ENTRY_DONE)
                .set(ImmunePlan::getPreEntryHandledBy, UserContext.userId())
                .set(ImmunePlan::getPreEntryHandledAt, BeijingTime.now())
                .set(ImmunePlan::getVersion, version + 1));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CHANGED, "该项目已处理，请刷新");
        }
        audit.record(plan.getFarmId(), "IMMUNE_PLAN", plan.getBatchId() == null ? planId : plan.getBatchId(),
                "PRE_ENTRY_CONFIRM", planTarget(plan), support.batchNo(plan.getBatchId()),
                Map.of("status", HealthCodes.PLAN_PRE_ENTRY),
                Map.of("status", HealthCodes.PLAN_PRE_ENTRY_DONE));
        return overview(requireBatchId(plan));
    }

    @Override
    @Transactional
    public ImmunePlanVO preEntryJoin(Long planId, Integer version) {
        support.requireManager("只有所有者和场长可以处理入栏前项目");
        ImmunePlan plan = requirePlan(planId);
        requireVersion(version);
        if (!HealthCodes.PLAN_PRE_ENTRY.equals(plan.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "该项目已处理，请刷新");
        }
        BatchInfo batch = plan.getBatchId() == null ? null : support.requireBatch(plan.getBatchId());
        if (batch != null && !batch.active()) {
            throw new BizException(ErrorCode.STATE_CHANGED, "批次已结束，未执行的计划已自动取消。");
        }
        LocalDate today = BeijingTime.today();
        int rows = planMapper.update(null, Wrappers.<ImmunePlan>lambdaUpdate()
                .eq(ImmunePlan::getId, planId).eq(ImmunePlan::getVersion, version)
                .set(ImmunePlan::getStatus, HealthCodes.PLAN_PENDING)
                .set(ImmunePlan::getPlanDate, today)
                .set(ImmunePlan::getPreEntryHandledBy, UserContext.userId())
                .set(ImmunePlan::getPreEntryHandledAt, BeijingTime.now())
                .set(ImmunePlan::getVersion, version + 1));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CHANGED, "该项目已处理，请刷新");
        }
        audit.record(plan.getFarmId(), "IMMUNE_PLAN", plan.getBatchId() == null ? planId : plan.getBatchId(),
                "PRE_ENTRY_JOIN", planTarget(plan), support.batchNo(plan.getBatchId()),
                Map.of("status", HealthCodes.PLAN_PRE_ENTRY), Map.of("status", HealthCodes.PLAN_PENDING));
        return toPlanVO(requirePlan(planId), batch, batch == null ? null : support.mainPenId(batch),
                support.memberNames());
    }

    @Override
    @Transactional
    public ImmunePlanOverviewVO preEntryUndo(Long planId, Integer version) {
        support.requireManager("只有所有者和场长可以处理入栏前项目");
        ImmunePlan plan = requirePlan(planId);
        requireVersion(version);
        LocalDateTime handledAt = plan.getPreEntryHandledAt();
        boolean within = handledAt != null && handledAt.plusMinutes(10).isAfter(BeijingTime.now());
        if (!HealthCodes.PLAN_PRE_ENTRY_DONE.equals(plan.getStatus()) || !within) {
            throw new BizException(ErrorCode.STATE_CHANGED, "已无法撤销（超过 10 分钟或状态已变化）");
        }
        int rows = planMapper.update(null, Wrappers.<ImmunePlan>lambdaUpdate()
                .eq(ImmunePlan::getId, planId).eq(ImmunePlan::getVersion, version)
                .set(ImmunePlan::getStatus, HealthCodes.PLAN_PRE_ENTRY)
                .set(ImmunePlan::getPreEntryHandledBy, null)
                .set(ImmunePlan::getPreEntryHandledAt, null)
                .set(ImmunePlan::getVersion, version + 1));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CHANGED, "已无法撤销（超过 10 分钟或状态已变化）");
        }
        audit.record(plan.getFarmId(), "IMMUNE_PLAN", plan.getBatchId() == null ? planId : plan.getBatchId(),
                "PRE_ENTRY_UNDO", planTarget(plan), support.batchNo(plan.getBatchId()),
                Map.of("status", HealthCodes.PLAN_PRE_ENTRY_DONE), Map.of("status", HealthCodes.PLAN_PRE_ENTRY));
        return overview(requireBatchId(plan));
    }

    // ===== API-D06 调整日期 =====

    @Override
    @Transactional
    public ImmunePlanVO postpone(Long planId, ImmunePlanPostponeRequest request) {
        support.requireManager("只有所有者和场长可以调整计划日期");
        ImmunePlan plan = requirePlan(planId);
        if (request == null || request.getNewDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择新的计划日期");
        }
        LocalDate newDate = request.getNewDate();
        if (newDate.isBefore(BeijingTime.today())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新日期不能早于今天");
        }
        if (newDate.equals(plan.getPlanDate())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新日期与原计划日期相同");
        }
        String reason = request.getReason() == null ? null : request.getReason().trim();
        if (reason == null || reason.isEmpty() || reason.length() > 50) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写延期原因");
        }
        if (!List.of(HealthCodes.PLAN_PENDING, HealthCodes.PLAN_POSTPONED).contains(plan.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "该项目当前不能调整日期");
        }
        requireVersion(request.getVersion());
        LocalDate oldDate = plan.getPlanDate();
        int rows = planMapper.update(null, Wrappers.<ImmunePlan>lambdaUpdate()
                .eq(ImmunePlan::getId, planId).eq(ImmunePlan::getVersion, request.getVersion())
                .set(ImmunePlan::getStatus, HealthCodes.PLAN_POSTPONED)
                .set(ImmunePlan::getPostponed, true)
                .set(ImmunePlan::getPostponeFromDate, oldDate)
                .set(ImmunePlan::getPlanDate, newDate)
                .set(ImmunePlan::getPostponeReason, reason)
                .set(ImmunePlan::getPostponedBy, UserContext.userId())
                .set(ImmunePlan::getPostponedAt, BeijingTime.now())
                .set(ImmunePlan::getVersion, request.getVersion() + 1));
        if (rows == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        audit.record(plan.getFarmId(), "IMMUNE_PLAN", plan.getBatchId() == null ? planId : plan.getBatchId(),
                "POSTPONE", planTarget(plan), support.batchNo(plan.getBatchId()),
                Map.of("plan_date", oldDate.toString()), Map.of("plan_date", newDate.toString(), "reason", reason));
        return reloadVO(planId);
    }

    // ===== API-D07 添加 =====

    @Override
    @Transactional
    public ImmunePlanVO create(Long batchId, ImmunePlanCreateRequest request) {
        support.requireManager("只有所有者和场长可以添加免疫项目");
        BatchInfo batch = support.requireBatch(batchId);
        support.checkBatchAccess(batch);
        if (!batch.active()) {
            throw new BizException(ErrorCode.STATE_CHANGED, "批次已结束，未执行的计划已自动取消。");
        }
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写免疫项目");
        }
        if (drugRows().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "本场还没有疫苗档案。请先在兽药与疫苗库存中新增疫苗。");
        }
        FarmDrugRow drug = request.getDrugId() == null ? null : drugRows().get(request.getDrugId());
        if (drug == null || !HealthCodes.CATEGORY_VACCINE.equals(drug.getCategory())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择疫苗");
        }
        DrugCatalogInfo catalog = drugCatalogQueryService.requireCatalog(drug.getCatalogId());
        drugCatalogQueryService.assertNotBanned(drug.getCatalogId(), support.species(batch.categoryId()));
        String planMode = request.getPlanMode();
        if (!HealthCodes.PLAN_BY_DAY_AGE.equals(planMode) && !HealthCodes.PLAN_BY_DATE.equals(planMode)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择计划方式");
        }
        int entryAge = batch.entryAgeDays() == null ? 0 : batch.entryAgeDays();
        LocalDate planDate;
        if (HealthCodes.PLAN_BY_DAY_AGE.equals(planMode)) {
            if (request.getPlanAgeDay() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请填写计划日龄");
            }
            if (request.getPlanAgeDay() < entryAge) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "不能早于批次当前日龄 " + entryAge + " 日龄");
            }
            if (request.getPlanAgeDay() > 3650) {
                throw new BizException(ErrorCode.PARAM_INVALID, "计划日龄不能大于 3650");
            }
            planDate = batch.entryDate().plusDays(request.getPlanAgeDay() - (long) entryAge);
        } else {
            planDate = request.getPlanDate();
            if (planDate == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请选择计划日期");
            }
            if (planDate.isBefore(BeijingTime.today())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "计划日期不能早于今天");
            }
        }
        if (request.getDose() == null || request.getDose().signum() <= 0
                || request.getDose().compareTo(new BigDecimal("9999.999")) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "剂量需大于 0");
        }
        if (request.getMethodCode() == null || request.getMethodCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择接种方式");
        }
        if (request.getImmuneRound() != null && request.getImmuneRound().length() > 20) {
            throw new BizException(ErrorCode.PARAM_INVALID, "免次不能超过 20 字");
        }
        ImmunePlan plan = new ImmunePlan();
        plan.setFarmId(support.farmId());
        plan.setTargetType(HealthCodes.TARGET_BATCH);
        plan.setBatchId(batchId);
        plan.setSource(HealthCodes.SOURCE_MANUAL);
        plan.setPreEntry(false);
        plan.setCatalogId(drug.getCatalogId());
        plan.setDrugId(drug.getId());
        plan.setVaccineName(catalog.name());
        plan.setImmuneRound(request.getImmuneRound());
        plan.setPlanMode(planMode);
        plan.setPlanAgeDay(request.getPlanAgeDay());
        plan.setOriginalPlanDate(planDate);
        plan.setPlanDate(planDate);
        plan.setDueTime(parseTime(request.getDueTime(), DEFAULT_DUE_TIME));
        plan.setDose(request.getDose());
        plan.setDoseUnit(catalog.vaccineDoseUnit() == null ? "DOSE_PER_HEAD" : catalog.vaccineDoseUnit());
        plan.setMethodCode(request.getMethodCode());
        plan.setStatus(HealthCodes.PLAN_PENDING);
        plan.setPostponed(false);
        plan.setManualDone(false);
        plan.setMissedNotified(false);
        plan.setVersion(0);
        planMapper.insert(plan);
        audit.record(plan.getFarmId(), "IMMUNE_PLAN", batchId, "CREATE", planTarget(plan), batch.batchNo(),
                null, Map.of("vaccine", plan.getVaccineName(), "plan_date", planDate.toString()));
        return toPlanVO(plan, batch, support.mainPenId(batch), support.memberNames());
    }

    // ===== API-D08 修改 =====

    @Override
    @Transactional
    public ImmunePlanVO update(Long planId, ImmunePlanUpdateRequest request) {
        support.requireManager("只有所有者和场长可以修改免疫项目");
        ImmunePlan plan = requirePlan(planId);
        if (!List.of(HealthCodes.PLAN_PENDING, HealthCodes.PLAN_POSTPONED).contains(plan.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "该项目当前不能修改");
        }
        if (request == null || (request.getDose() == null && request.getMethodCode() == null
                && request.getDueTime() == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写要修改的内容");
        }
        requireVersion(request.getVersion());
        var wrapper = Wrappers.<ImmunePlan>lambdaUpdate()
                .eq(ImmunePlan::getId, planId).eq(ImmunePlan::getVersion, request.getVersion());
        Map<String, Object> after = new LinkedHashMap<>();
        if (request.getDose() != null) {
            if (request.getDose().signum() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "剂量需大于 0");
            }
            wrapper.set(ImmunePlan::getDose, request.getDose());
            after.put("dose", request.getDose());
        }
        if (request.getMethodCode() != null) {
            if (request.getMethodCode().isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请选择接种方式");
            }
            wrapper.set(ImmunePlan::getMethodCode, request.getMethodCode());
            after.put("method_code", request.getMethodCode());
        }
        if (request.getDueTime() != null) {
            LocalTime time = parseTime(request.getDueTime(), null);
            if (time == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "截止时刻格式为 HH:mm");
            }
            wrapper.set(ImmunePlan::getDueTime, time);
            after.put("due_time", request.getDueTime());
        }
        wrapper.set(ImmunePlan::getVersion, request.getVersion() + 1);
        int rows = planMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        audit.record(plan.getFarmId(), "IMMUNE_PLAN", plan.getBatchId() == null ? planId : plan.getBatchId(),
                "UPDATE", planTarget(plan), support.batchNo(plan.getBatchId()), null, after);
        return reloadVO(planId);
    }

    // ===== API-D09 删除 =====

    @Override
    @Transactional
    public void delete(Long planId, Integer version) {
        support.requireManager("只有所有者和场长可以删除免疫项目");
        ImmunePlan plan = requirePlan(planId);
        requireVersion(version);
        if (!List.of(HealthCodes.PLAN_PENDING, HealthCodes.PLAN_POSTPONED).contains(plan.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "该项目当前不能删除");
        }
        int rows = planMapper.update(null, Wrappers.<ImmunePlan>lambdaUpdate()
                .eq(ImmunePlan::getId, planId).eq(ImmunePlan::getVersion, version)
                .set(ImmunePlan::getStatus, HealthCodes.PLAN_CANCELLED)
                .set(ImmunePlan::getCancelType, HealthCodes.CANCEL_USER_DELETED)
                .set(ImmunePlan::getCancelledBy, UserContext.userId())
                .set(ImmunePlan::getCancelledAt, BeijingTime.now())
                .set(ImmunePlan::getVersion, version + 1));
        if (rows == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        audit.record(plan.getFarmId(), "IMMUNE_PLAN", plan.getBatchId() == null ? planId : plan.getBatchId(),
                "CANCEL", planTarget(plan), support.batchNo(plan.getBatchId()),
                Map.of("status", plan.getStatus()), Map.of("status", HealthCodes.PLAN_CANCELLED));
    }

    // ===== 内部方法 =====

    private ImmunePlan requirePlan(Long planId) {
        ImmunePlan plan = planId == null ? null : planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return plan;
    }

    private Long requireBatchId(ImmunePlan plan) {
        if (plan.getBatchId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "种畜栏舍项目请在登记接种中处理");
        }
        return plan.getBatchId();
    }

    private void requireVersion(Integer version) {
        if (version == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 version");
        }
    }

    private ImmunePlanVO reloadVO(Long planId) {
        ImmunePlan plan = requirePlan(planId);
        BatchInfo batch = plan.getBatchId() == null ? null : support.requireBatch(plan.getBatchId());
        return toPlanVO(plan, batch, batch == null ? plan.getPenId() : support.mainPenId(batch),
                support.memberNames());
    }

    private ImmunePlanVO toPlanVO(ImmunePlan plan, BatchInfo batch, Long mainPenId, Map<Long, String> names) {
        ImmunePlanVO vo = new ImmunePlanVO();
        vo.setId(plan.getId());
        vo.setVersion(plan.getVersion());
        vo.setVaccineName(plan.getVaccineName());
        vo.setImmuneRound(plan.getImmuneRound());
        vo.setPlanMode(plan.getPlanMode());
        vo.setPlanDate(plan.getPlanDate());
        vo.setIsToday(plan.getPlanDate() != null && plan.getPlanDate().equals(BeijingTime.today()));
        vo.setDueTime(HealthFormat.timeText(plan.getDueTime()));
        vo.setOverdue(isOverdue(plan));
        vo.setAgeAtPlan(ageAtPlan(plan, batch));
        vo.setPostponed(Boolean.TRUE.equals(plan.getPostponed()));
        vo.setPostponeFromDate(plan.getPostponeFromDate());
        vo.setPostponedByName(nameOf(plan.getPostponedBy(), names));
        Long penId = batch == null ? plan.getPenId() : mainPenId;
        vo.setResponsibleText(support.responsibleText(penId));
        vo.setRemindText(REMIND_TEXT);
        vo.setStatus(plan.getStatus());
        vo.setStatusText(HealthCodes.immuneStatusText(plan.getStatus()));
        vo.setDose(plan.getDose());
        vo.setDoseUnit(HealthCodes.doseUnitText(plan.getDoseUnit()));
        vo.setMethodCode(plan.getMethodCode());
        vo.setMethodName(support.dictName("VACCINATE_METHOD", plan.getMethodCode()));
        vo.setManualDone(Boolean.TRUE.equals(plan.getManualDone()));
        vo.setCancelType(plan.getCancelType());
        vo.setCancelledByName(nameOf(plan.getCancelledBy(), names));
        if (plan.getRecordId() != null) {
            ImmuneRecord record = recordMapper.selectById(plan.getRecordId());
            if (record != null) {
                vo.setRecordId(record.getId());
                vo.setRecordDate(record.getVaccinateDate());
                vo.setRecordUserName(nameOf(record.getExecutorId(), names));
                vo.setRecordQuantity(record.getQuantity());
                vo.setBackfill(Boolean.TRUE.equals(record.getBackfill()));
            }
        }
        vo.setActions(actions(plan, batch));
        return vo;
    }

    private List<String> actions(ImmunePlan plan, BatchInfo batch) {
        List<String> actions = new ArrayList<>();
        if (batch != null && !batch.active()) {
            return actions;
        }
        boolean manager = support.managerView();
        switch (plan.getStatus()) {
            case HealthCodes.PLAN_PENDING, HealthCodes.PLAN_POSTPONED -> {
                actions.add(HealthCodes.ACTION_REGISTER);
                if (manager) {
                    actions.add(HealthCodes.ACTION_POSTPONE);
                    actions.add(HealthCodes.ACTION_EDIT);
                    actions.add(HealthCodes.ACTION_DELETE);
                }
            }
            case HealthCodes.PLAN_MISSED -> {
                if (manager) {
                    actions.add(HealthCodes.ACTION_BACKFILL);
                }
            }
            default -> {
            }
        }
        return actions;
    }

    private boolean isOverdue(ImmunePlan plan) {
        if (!List.of(HealthCodes.PLAN_PENDING, HealthCodes.PLAN_POSTPONED).contains(plan.getStatus())) {
            return false;
        }
        LocalTime due = plan.getDueTime() == null ? DEFAULT_DUE_TIME : plan.getDueTime();
        return LocalDateTime.of(plan.getPlanDate(), due).isBefore(BeijingTime.now());
    }

    private Integer ageAtPlan(ImmunePlan plan, BatchInfo batch) {
        if (batch == null || batch.entryDate() == null || batch.entryAgeDays() == null) {
            return plan.getPlanAgeDay();
        }
        return batch.entryAgeDays()
                + (int) java.time.temporal.ChronoUnit.DAYS.between(batch.entryDate(), plan.getPlanDate());
    }

    private int dayAge(BatchInfo batch, int entryAge) {
        if (batch.entryDate() == null) {
            return entryAge;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(batch.entryDate(), BeijingTime.today());
        return entryAge + (int) Math.max(days, 0);
    }

    private ImmunePlanOptionVO optionOf(ImmunePlan plan, BatchInfo batch, Long penId) {
        ImmunePlanOptionVO vo = new ImmunePlanOptionVO();
        vo.setPlanId(plan.getId());
        vo.setTargetType(plan.getTargetType());
        vo.setBatchId(plan.getBatchId());
        vo.setBatchNo(batch == null ? null : batch.batchNo());
        vo.setPenName(support.penName(penId));
        vo.setVaccineName(plan.getVaccineName());
        vo.setImmuneRound(plan.getImmuneRound());
        vo.setCurrentCount(currentCount(plan, batch));
        vo.setPlanDate(plan.getPlanDate());
        vo.setPlanDateText(HealthFormat.dateText(plan.getPlanDate()));
        vo.setStatus(plan.getStatus());
        vo.setStatusText(HealthCodes.immuneStatusText(plan.getStatus()));
        return vo;
    }

    private int currentCount(ImmunePlan plan, BatchInfo batch) {
        if (batch != null) {
            return batch.currentCount() == null ? 0 : batch.currentCount();
        }
        return plan.getPenId() == null ? 0 : referenceMapper.countBreedingAnimals(plan.getFarmId(), plan.getPenId());
    }

    private LocalDate dateMin(ImmunePlan plan, BatchInfo batch, boolean backfill) {
        if (backfill) {
            return plan.getPlanDate();
        }
        if (batch != null && batch.entryDate() != null) {
            return batch.entryDate();
        }
        if (plan.getOriginalPlanDate() != null) {
            return plan.getOriginalPlanDate();
        }
        return plan.getCreatedAt() == null ? BeijingTime.today() : plan.getCreatedAt().toLocalDate();
    }

    private List<DrugLotOption> lots(Long drugId, String purpose, BigDecimal qty) {
        if (drugId == null) {
            return List.of();
        }
        try {
            List<DrugLotOption> lots = drugStockPort.lotsFor(support.farmId(), drugId, purpose, qty);
            return lots == null ? List.of() : lots;
        } catch (BizException e) {
            return List.of();
        }
    }

    private DrugLotOption firstSelectable(List<DrugLotOption> lots) {
        for (DrugLotOption lot : lots) {
            if (lot.lotStatus() == null || "NORMAL".equals(lot.lotStatus()) || "NEAR_EXPIRY".equals(lot.lotStatus())) {
                return lot;
            }
        }
        return null;
    }

    private DrugLotVO lotVO(DrugLotOption option) {
        DrugLotVO vo = new DrugLotVO();
        vo.setLotId(option.lotId());
        vo.setLotNo(option.lotNo());
        vo.setExpireDate(option.expireDate());
        vo.setLotStatus(option.lotStatus());
        vo.setLotStatusText(option.lotStatusText() == null
                ? HealthCodes.lotStatusText(option.lotStatus()) : option.lotStatusText());
        vo.setDaysToExpire(option.daysToExpire());
        vo.setStock(option.stock());
        vo.setAvgPrice(option.avgPrice());
        vo.setPriorityOut(option.priorityOut());
        vo.setInsufficient(option.insufficient());
        vo.setCanGoScrap(option.canGoScrap());
        return vo;
    }

    private String noLotText(String vaccineName) {
        String name = vaccineName == null ? "该疫苗" : vaccineName;
        return support.staffView()
                ? name + "没有可用的库存批号。" + HealthCodes.STAFF_NO_LOT_SUFFIX
                : name + "没有可用的库存批号。请先登记入库。";
    }

    private Map<Long, FarmDrugRow> drugRows() {
        Map<Long, FarmDrugRow> map = new LinkedHashMap<>();
        for (FarmDrugRow row : referenceMapper.selectDrugs(support.farmId())) {
            map.put(row.getId(), row);
        }
        return map;
    }

    private FarmDrugRow drugById(Long drugId) {
        return drugRows().get(drugId);
    }

    private Map<Long, BigDecimal> stockByDrug() {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (DrugStockRow row : referenceMapper.selectAvailableStock(support.farmId(), BeijingTime.today())) {
            map.put(row.getDrugId(), row.getStockQty() == null ? BigDecimal.ZERO : row.getStockQty());
        }
        return map;
    }

    private String nameOf(Long userId, Map<Long, String> names) {
        if (userId == null) {
            return null;
        }
        if (userId.equals(UserContext.userId())) {
            return "你";
        }
        String name = names.get(userId);
        return name == null ? support.userName(userId) : name;
    }

    private String planTarget(ImmunePlan plan) {
        return plan.getVaccineName() + (plan.getImmuneRound() == null ? "" : " " + plan.getImmuneRound());
    }

    private boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase().contains(keyword.toLowerCase());
    }

    private LocalTime parseTime(String text, LocalTime defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        String value = text.trim();
        try {
            return LocalTime.parse(value.length() > 5 ? value.substring(0, 5) : value);
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }
}
