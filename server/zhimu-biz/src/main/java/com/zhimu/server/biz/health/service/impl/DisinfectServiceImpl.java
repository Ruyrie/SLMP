package com.zhimu.server.biz.health.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.health.entity.DisinfectPlan;
import com.zhimu.server.biz.health.entity.DisinfectPlanPen;
import com.zhimu.server.biz.health.entity.DisinfectRecord;
import com.zhimu.server.biz.health.entity.DisinfectTask;
import com.zhimu.server.biz.health.mapper.DisinfectPlanMapper;
import com.zhimu.server.biz.health.mapper.DisinfectPlanPenMapper;
import com.zhimu.server.biz.health.mapper.DisinfectRecordMapper;
import com.zhimu.server.biz.health.mapper.DisinfectTaskMapper;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.query.DisinfectPlanQuery;
import com.zhimu.server.biz.health.query.DisinfectTaskQuery;
import com.zhimu.server.biz.health.query.DisinfectantCatalogRow;
import com.zhimu.server.biz.health.query.DisinfectantQuery;
import com.zhimu.server.biz.health.query.FarmDrugRow;
import com.zhimu.server.biz.health.request.DisinfectPlanPauseRequest;
import com.zhimu.server.biz.health.request.DisinfectPlanPreviewRequest;
import com.zhimu.server.biz.health.request.DisinfectPlanSaveRequest;
import com.zhimu.server.biz.health.request.DisinfectRecordCreateRequest;
import com.zhimu.server.biz.health.request.DisinfectRecordUpdateRequest;
import com.zhimu.server.biz.health.service.DisinfectService;
import com.zhimu.server.biz.health.support.HealthAudit;
import com.zhimu.server.biz.health.support.HealthCodes;
import com.zhimu.server.biz.health.support.HealthFormat;
import com.zhimu.server.biz.health.support.HealthSupport;
import com.zhimu.server.biz.health.vo.DisinfectPlanDetailVO;
import com.zhimu.server.biz.health.vo.DisinfectPlanPreviewVO;
import com.zhimu.server.biz.health.vo.DisinfectPlanVO;
import com.zhimu.server.biz.health.vo.DisinfectRecordVO;
import com.zhimu.server.biz.health.vo.DisinfectTaskOptionVO;
import com.zhimu.server.biz.health.vo.DisinfectTaskPageVO;
import com.zhimu.server.biz.health.vo.DisinfectTaskVO;
import com.zhimu.server.biz.health.vo.DisinfectantOptionVO;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.biz.stock.api.DrugLotOption;
import com.zhimu.server.biz.stock.api.DrugStockCommand;
import com.zhimu.server.biz.stock.api.DrugStockPort;
import com.zhimu.server.biz.stock.api.StockConsumeResult;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** API-D28–D40 消毒计划、任务与记录实现：任务生成、暂停 / 启用、登记与照片。 */
@Service
@RequiredArgsConstructor
public class DisinfectServiceImpl implements DisinfectService {

    private static final LocalTime DEFAULT_DUE_TIME = LocalTime.of(17, 0);
    private static final int WINDOW_DAYS = 89;

    private final DisinfectPlanMapper planMapper;
    private final DisinfectPlanPenMapper planPenMapper;
    private final DisinfectTaskMapper taskMapper;
    private final DisinfectRecordMapper recordMapper;
    private final HealthReferenceMapper referenceMapper;
    private final HealthSupport support;
    private final HealthAudit audit;
    private final BizMediaService bizMediaService;
    private final DrugStockPort drugStockPort;
    private final DisinfectTaskGenerator taskGenerator;
    private final HealthTodoSource todoSource;

    // ===== API-D28 计划列表 =====

    @Override
    public PageVO<DisinfectPlanVO> plans(DisinfectPlanQuery query) {
        support.requireManager("只有所有者和场长可以查看消毒计划");
        int pageNo = query == null ? 1 : query.pageOrDefault();
        int size = query == null ? 20 : query.sizeOrDefault();
        String status = query == null ? null : query.getStatus();
        var wrapper = Wrappers.<DisinfectPlan>lambdaQuery();
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(DisinfectPlan::getStatus, status.toUpperCase());
        }
        wrapper.orderByDesc(DisinfectPlan::getId);
        Page<DisinfectPlan> result = planMapper.selectPage(new Page<>(pageNo, size), wrapper);
        List<DisinfectPlanVO> list = new ArrayList<>();
        Map<Long, List<Long>> penIdsByPlan = penIdsByPlan();
        for (DisinfectPlan plan : result.getRecords()) {
            list.add(toPlanVO(plan, penIdsByPlan.getOrDefault(plan.getId(), List.of())));
        }
        list.sort(Comparator.comparing((DisinfectPlanVO vo) -> HealthCodes.DISINFECT_PLAN_ENDED.equals(vo.getStatus()))
                .thenComparing(DisinfectPlanVO::getId, Comparator.reverseOrder()));
        Map<String, Integer> tabCounts = new LinkedHashMap<>();
        tabCounts.put("all", planMapper.selectCount(null).intValue());
        tabCounts.put("active", planMapper.selectCount(Wrappers.<DisinfectPlan>lambdaQuery()
                .eq(DisinfectPlan::getStatus, HealthCodes.DISINFECT_PLAN_ACTIVE)).intValue());
        tabCounts.put("paused", planMapper.selectCount(Wrappers.<DisinfectPlan>lambdaQuery()
                .eq(DisinfectPlan::getStatus, HealthCodes.DISINFECT_PLAN_PAUSED)).intValue());
        return PageVO.of(list, result.getTotal(), pageNo, size).withTabCounts(tabCounts);
    }

    // ===== API-D29 详情 =====

    @Override
    public DisinfectPlanDetailVO plan(Long planId) {
        support.requireManager("只有所有者和场长可以查看消毒计划");
        DisinfectPlan plan = requirePlan(planId);
        List<Long> penIds = penIdsOf(planId);
        DisinfectPlanVO base = toPlanVO(plan, penIds);
        DisinfectPlanDetailVO vo = new DisinfectPlanDetailVO();
        copy(base, vo);
        vo.setCatalogId(plan.getCatalogId());
        vo.setDrugId(plan.getDrugId());
        Map<Long, PenInfo> pens = support.pens(penIds);
        for (Long penId : penIds) {
            DisinfectPlanDetailVO.Pen pen = new DisinfectPlanDetailVO.Pen();
            pen.setPenId(penId);
            PenInfo info = pens.get(penId);
            pen.setPenName(info == null ? support.penName(penId) : info.penName());
            pen.setExecutorText(support.executorText(penId));
            vo.getPens().add(pen);
        }
        return vo;
    }

    // ===== API-D30 预估 =====

    @Override
    public DisinfectPlanPreviewVO preview(DisinfectPlanPreviewRequest request) {
        support.requireManager("只有所有者和场长可以查看消毒计划");
        DisinfectPlanPreviewVO vo = new DisinfectPlanPreviewVO();
        boolean ready = request != null && request.getPenIds() != null && !request.getPenIds().isEmpty()
                && request.getCycleDays() != null && request.getCycleDays() > 0
                && request.getStartDate() != null && request.getEndDate() != null;
        if (!ready) {
            vo.setReady(false);
            vo.setReadyText("填写栏舍、周期和起止日期后显示将生成的任务数");
            return vo;
        }
        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();
        int cycle = request.getCycleDays();
        long span = ChronoUnit.DAYS.between(start, end);
        int times = span < 0 ? 0 : (int) (span / cycle) + 1;
        List<Long> penIds = new ArrayList<>(new LinkedHashSet<>(request.getPenIds()));
        vo.setReady(true);
        vo.setTimes(times);
        vo.setTaskCount(times * penIds.size());
        vo.setGenerateNowCount(generateCount(start, end, cycle, penIds.size()));
        vo.setPenText(penText(penIds));
        vo.setRemindText(HealthFormat.spanText(start, end) + "共 " + times + " 次，每次每个栏舍 1 条。"
                + "任务日期前 1 天和当天提醒你" + managerSuffix() + "。");
        vo.setRegenerateFrom(request.getPlanId() == null ? null : BeijingTime.today().plusDays(1));
        return vo;
    }

    private int generateCount(LocalDate start, LocalDate end, int cycle, int penSize) {
        LocalDate from = start.isBefore(BeijingTime.today()) ? BeijingTime.today() : start;
        LocalDate to = end.isAfter(BeijingTime.today().plusDays(WINDOW_DAYS))
                ? BeijingTime.today().plusDays(WINDOW_DAYS) : end;
        int count = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            long diff = ChronoUnit.DAYS.between(start, date);
            if (diff < 0 || diff % cycle != 0) {
                continue;
            }
            count += penSize;
        }
        return count;
    }

    private String penText(List<Long> penIds) {
        int noStaff = 0;
        List<String> staffNames = new ArrayList<>();
        for (Long penId : penIds) {
            String names = support.penResponsibleNames(penId);
            if (names == null) {
                noStaff++;
            } else {
                staffNames.add(names);
            }
        }
        String base = "已选 " + penIds.size() + " 个栏舍，系统为每个栏舍各生成一条任务。";
        if (noStaff == penIds.size()) {
            return base + "这" + (penIds.size() == 2 ? "两个" : penIds.size() == 1 ? "个" : "些")
                    + "栏舍没有负责员工，任务由" + support.executorText(penIds.get(0)) + "执行。";
        }
        return base + "任务由" + String.join("、", staffNames) + "执行。";
    }

    private String managerSuffix() {
        List<String> names = new ArrayList<>();
        for (var row : support.members()) {
            if ("MANAGER".equals(row.getRole()) && row.getName() != null) {
                names.add(row.getName());
            }
        }
        return names.isEmpty() ? "" : "和" + String.join("、", names);
    }

    // ===== API-D31 新建 =====

    @Override
    @Transactional
    public DisinfectPlanVO create(DisinfectPlanSaveRequest request) {
        support.requireManager("只有所有者和场长可以管理消毒计划");
        validate(request, null);
        DisinfectantCatalogRow catalog = catalog(request.getCatalogId());
        FarmDrugRow drug = drugByCatalog(request.getCatalogId());
        DisinfectPlan plan = new DisinfectPlan();
        plan.setFarmId(support.farmId());
        plan.setCycleDays(request.getCycleDays());
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setDueTime(parseTime(request.getDueTime()));
        plan.setCatalogId(request.getCatalogId());
        plan.setDrugId(drug == null ? null : drug.getId());
        plan.setDisinfectantName(catalog.getName());
        plan.setConcentration(request.getConcentration());
        plan.setMethodCode(request.getMethodCode());
        plan.setStatus(HealthCodes.DISINFECT_PLAN_ACTIVE);
        plan.setVersion(0);
        planMapper.insert(plan);
        List<Long> penIds = new ArrayList<>(new LinkedHashSet<>(request.getPenIds()));
        savePens(plan.getId(), penIds);
        LocalDate today = BeijingTime.today();
        LocalDate from = plan.getStartDate().isBefore(today) ? today : plan.getStartDate();
        LocalDate to = min(plan.getEndDate(), today.plusDays(WINDOW_DAYS));
        int taskCount = generate(plan, penIds, from, to);
        plan.setGeneratedUntil(to);
        planMapper.updateById(plan);
        audit.record(plan.getFarmId(), "DISINFECT_PLAN", plan.getId(), "CREATE", plan.getDisinfectantName(),
                null, null, Map.of("cycle_days", plan.getCycleDays(), "pen_ids", penIds));
        DisinfectPlanVO vo = toPlanVO(planMapper.selectById(plan.getId()), penIds);
        vo.setTaskCount(taskCount);
        vo.setToast("已保存并启用，生成 " + taskCount + " 条消毒任务");
        return vo;
    }

    // ===== API-D32 修改 =====

    @Override
    @Transactional
    public DisinfectPlanVO update(Long planId, DisinfectPlanSaveRequest request) {
        support.requireManager("只有所有者和场长可以管理消毒计划");
        DisinfectPlan plan = requirePlan(planId);
        if (request == null || request.getVersion() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 version");
        }
        validate(request, plan);
        DisinfectantCatalogRow catalog = catalog(request.getCatalogId());
        FarmDrugRow drug = drugByCatalog(request.getCatalogId());
        LocalDate tomorrow = BeijingTime.today().plusDays(1);
        cancelFrom(planId, tomorrow, "计划修改");
        int rows = planMapper.update(null, Wrappers.<DisinfectPlan>lambdaUpdate()
                .eq(DisinfectPlan::getId, planId).eq(DisinfectPlan::getVersion, request.getVersion())
                .set(DisinfectPlan::getCycleDays, request.getCycleDays())
                .set(DisinfectPlan::getStartDate, request.getStartDate())
                .set(DisinfectPlan::getEndDate, request.getEndDate())
                .set(DisinfectPlan::getDueTime, parseTime(request.getDueTime()))
                .set(DisinfectPlan::getCatalogId, request.getCatalogId())
                .set(DisinfectPlan::getDrugId, drug == null ? null : drug.getId())
                .set(DisinfectPlan::getDisinfectantName, catalog.getName())
                .set(DisinfectPlan::getConcentration, request.getConcentration())
                .set(DisinfectPlan::getMethodCode, request.getMethodCode())
                .set(DisinfectPlan::getStatus, HealthCodes.DISINFECT_PLAN_ACTIVE)
                .set(DisinfectPlan::getVersion, request.getVersion() + 1));
        if (rows == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        List<Long> penIds = new ArrayList<>(new LinkedHashSet<>(request.getPenIds()));
        planPenMapper.delete(Wrappers.<DisinfectPlanPen>lambdaQuery().eq(DisinfectPlanPen::getPlanId, planId));
        savePens(planId, penIds);
        DisinfectPlan updated = requirePlan(planId);
        LocalDate from = max(updated.getStartDate(), tomorrow);
        LocalDate to = min(updated.getEndDate(), BeijingTime.today().plusDays(WINDOW_DAYS));
        if (!from.isAfter(to)) {
            generate(updated, penIds, from, to);
            planMapper.update(null, Wrappers.<DisinfectPlan>lambdaUpdate()
                    .eq(DisinfectPlan::getId, planId).set(DisinfectPlan::getGeneratedUntil, to));
        }
        audit.record(updated.getFarmId(), "DISINFECT_PLAN", planId, "UPDATE", updated.getDisinfectantName(),
                null, null, Map.of("cycle_days", updated.getCycleDays(), "pen_ids", penIds));
        return toPlanVO(requirePlan(planId), penIds);
    }

    // ===== API-D33 暂停 =====

    @Override
    @Transactional
    public DisinfectPlanVO pause(Long planId, DisinfectPlanPauseRequest request) {
        support.requireManager("只有所有者和场长可以管理消毒计划");
        DisinfectPlan plan = requirePlan(planId);
        if (!HealthCodes.DISINFECT_PLAN_ACTIVE.equals(plan.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "计划当前不能暂停");
        }
        if (request == null || request.getVersion() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 version");
        }
        String reason = request.getReason() == null ? null : request.getReason().trim();
        if (reason != null && reason.length() > 30) {
            throw new BizException(ErrorCode.PARAM_INVALID, "暂停原因不能超过 30 字");
        }
        LocalDate today = BeijingTime.today();
        taskMapper.update(null, Wrappers.<DisinfectTask>lambdaUpdate()
                .eq(DisinfectTask::getPlanId, planId)
                .eq(DisinfectTask::getStatus, HealthCodes.TASK_PENDING)
                .ge(DisinfectTask::getTaskDate, today)
                .set(DisinfectTask::getStatus, HealthCodes.TASK_PAUSED));
        int rows = planMapper.update(null, Wrappers.<DisinfectPlan>lambdaUpdate()
                .eq(DisinfectPlan::getId, planId).eq(DisinfectPlan::getVersion, request.getVersion())
                .set(DisinfectPlan::getStatus, HealthCodes.DISINFECT_PLAN_PAUSED)
                .set(DisinfectPlan::getPauseReason, reason)
                .set(DisinfectPlan::getPausedBy, UserContext.userId())
                .set(DisinfectPlan::getPausedAt, BeijingTime.now())
                .set(DisinfectPlan::getVersion, request.getVersion() + 1));
        if (rows == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        audit.record(plan.getFarmId(), "DISINFECT_PLAN", planId, "PAUSE", plan.getDisinfectantName(), null,
                Map.of("status", HealthCodes.DISINFECT_PLAN_ACTIVE),
                Map.of("status", HealthCodes.DISINFECT_PLAN_PAUSED, "reason", reason == null ? "" : reason));
        todoSource.generate(TodoSource.TodoSourceContext.builder().farmId(plan.getFarmId()).build());
        DisinfectPlanVO vo = toPlanVO(requirePlan(planId), penIdsOf(planId));
        return vo;
    }

    // ===== API-D34 启用 =====

    @Override
    @Transactional
    public DisinfectPlanVO resume(Long planId, Integer version) {
        support.requireManager("只有所有者和场长可以管理消毒计划");
        DisinfectPlan plan = requirePlan(planId);
        if (!HealthCodes.DISINFECT_PLAN_PAUSED.equals(plan.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "计划当前不能启用");
        }
        if (version == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 version");
        }
        LocalDate today = BeijingTime.today();
        taskMapper.update(null, Wrappers.<DisinfectTask>lambdaUpdate()
                .eq(DisinfectTask::getPlanId, planId)
                .eq(DisinfectTask::getStatus, HealthCodes.TASK_PAUSED)
                .ge(DisinfectTask::getTaskDate, today)
                .set(DisinfectTask::getStatus, HealthCodes.TASK_PENDING));
        int rows = planMapper.update(null, Wrappers.<DisinfectPlan>lambdaUpdate()
                .eq(DisinfectPlan::getId, planId).eq(DisinfectPlan::getVersion, version)
                .set(DisinfectPlan::getStatus, HealthCodes.DISINFECT_PLAN_ACTIVE)
                .set(DisinfectPlan::getResumedAt, BeijingTime.now())
                .set(DisinfectPlan::getPauseReason, null)
                .set(DisinfectPlan::getVersion, version + 1));
        if (rows == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        List<Long> penIds = penIdsOf(planId);
        DisinfectPlan updated = requirePlan(planId);
        LocalDate from = max(updated.getStartDate(), today);
        LocalDate to = min(updated.getEndDate(), today.plusDays(WINDOW_DAYS));
        if (!from.isAfter(to)) {
            generate(updated, penIds, from, to);
        }
        audit.record(updated.getFarmId(), "DISINFECT_PLAN", planId, "RESUME", updated.getDisinfectantName(),
                null, Map.of("status", HealthCodes.DISINFECT_PLAN_PAUSED),
                Map.of("status", HealthCodes.DISINFECT_PLAN_ACTIVE));
        DisinfectPlanVO vo = toPlanVO(requirePlan(planId), penIds);
        LocalDate next = nextTaskDate(planId);
        vo.setToast("已启用计划" + (next == null ? "" : "，下次任务 " + HealthFormat.dateText(next)));
        return vo;
    }

    // ===== API-D35 消毒剂下拉 =====

    @Override
    public List<DisinfectantOptionVO> disinfectants(DisinfectantQuery query) {
        String keyword = query == null || query.getKeyword() == null ? null : query.getKeyword().trim();
        Map<Long, FarmDrugRow> drugs = drugsByCatalog();
        List<DisinfectantOptionVO> list = new ArrayList<>();
        for (DisinfectantCatalogRow row : referenceMapper.selectDisinfectantCatalogs()) {
            if (keyword != null && !keyword.isEmpty() && !contains(row.getName(), keyword)) {
                continue;
            }
            FarmDrugRow drug = drugs.get(row.getId());
            DisinfectantOptionVO vo = new DisinfectantOptionVO();
            vo.setCatalogId(row.getId());
            vo.setDrugId(drug == null ? null : drug.getId());
            vo.setName(row.getName());
            vo.setRecommendedConcentration(row.getConcentration());
            if (row.getMethodCodes() != null && !row.getMethodCodes().isBlank()) {
                for (String code : row.getMethodCodes().split(",")) {
                    if (!code.isBlank()) {
                        vo.getMethodCodes().add(code.trim());
                    }
                }
            }
            vo.setStockUnit(HealthCodes.unitText(drug == null ? row.getMeasureUnit() : drug.getUnit()));
            fillStockHint(vo, row, drug);
            list.add(vo);
        }
        return list;
    }

    private void fillStockHint(DisinfectantOptionVO vo, DisinfectantCatalogRow row, FarmDrugRow drug) {
        BigDecimal qty = BigDecimal.ZERO;
        DrugLotOption nearExpiry = null;
        if (drug != null) {
            for (DrugLotOption lot : support.lotsFor(drug.getId(), HealthCodes.PURPOSE_DISINFECT)) {
                if (lot.stock() != null && lot.stock().getValue() != null) {
                    qty = qty.add(lot.stock().getValue());
                }
                if ("NEAR_EXPIRY".equals(lot.lotStatus()) && nearExpiry == null) {
                    nearExpiry = lot;
                }
            }
        }
        String unit = vo.getStockUnit() == null ? "" : vo.getStockUnit();
        if (qty.signum() <= 0) {
            vo.setStockHintType("NO_STOCK");
            vo.setStockHintText(row.getName() + "暂无可用库存，登记消毒前需要先入库");
            return;
        }
        if (nearExpiry != null) {
            vo.setStockHintType("NEAR_EXPIRY");
            vo.setStockHintText("库存批号 " + nearExpiry.lotNo() + " 临期，剩 "
                    + (nearExpiry.daysToExpire() == null ? 0 : nearExpiry.daysToExpire()) + " 天");
            return;
        }
        vo.setStockHintType("NORMAL");
        vo.setStockHintText("结存 " + HealthFormat.numberText(qty) + " " + unit);
    }

    // ===== API-D36 任务列表 =====

    @Override
    public DisinfectTaskPageVO tasks(DisinfectTaskQuery query) {
        ensureTasks();
        int pageNo = query == null ? 1 : query.pageOrDefault();
        int size = query == null ? 20 : query.sizeOrDefault();
        String status = query == null || query.getStatus() == null ? "ALL" : query.getStatus().toUpperCase();
        Long penId = query == null ? null : query.getPenId();
        List<DisinfectTask> tasks = taskMapper.selectList(Wrappers.<DisinfectTask>lambdaQuery()
                .notIn(DisinfectTask::getStatus, HealthCodes.TASK_PAUSED, HealthCodes.TASK_CANCELLED)
                .eq(penId != null, DisinfectTask::getPenId, penId)
                .orderByAsc(DisinfectTask::getTaskDate).orderByDesc(DisinfectTask::getId));
        LocalDate today = BeijingTime.today();
        List<DisinfectTask> visible = new ArrayList<>();
        for (DisinfectTask task : tasks) {
            if (support.staffView() && !support.member().canAccessPen(task.getPenId())) {
                continue;
            }
            visible.add(task);
        }
        int pendingCount = 0;
        int doneCount = 0;
        int overdueCount = 0;
        for (DisinfectTask task : visible) {
            String s = taskStatus(task);
            switch (s) {
                case HealthCodes.TASK_PENDING -> pendingCount++;
                case HealthCodes.TASK_DONE -> doneCount++;
                case HealthCodes.TASK_OVERDUE -> overdueCount++;
                default -> {
                }
            }
        }
        List<DisinfectTask> filtered = new ArrayList<>();
        for (DisinfectTask task : visible) {
            String s = taskStatus(task);
            if ("OVERDUE".equals(status)) {
                if (HealthCodes.TASK_OVERDUE.equals(s)) {
                    filtered.add(task);
                }
            } else if ("PENDING".equals(status)) {
                if (HealthCodes.TASK_PENDING.equals(s) && !task.getTaskDate().isAfter(today.plusDays(1))) {
                    filtered.add(task);
                }
            } else if ("DONE".equals(status)) {
                if (HealthCodes.TASK_DONE.equals(s)) {
                    filtered.add(task);
                }
            } else {
                filtered.add(task);
            }
        }
        filtered.sort(taskComparator());
        int from = Math.min((pageNo - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<DisinfectTaskVO> list = new ArrayList<>();
        for (DisinfectTask task : filtered.subList(from, to)) {
            list.add(toTaskVO(task));
        }
        DisinfectTaskPageVO vo = new DisinfectTaskPageVO();
        vo.setList(list);
        vo.setTotal(filtered.size());
        vo.setPage(pageNo);
        vo.setSize(size);
        vo.setHasMore((long) pageNo * size < filtered.size());
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("pending", pendingCount);
        tabs.put("done", doneCount);
        tabs.put("overdue", overdueCount);
        vo.setTabCounts(tabs);
        if (support.managerView() && overdueCount > 0) {
            DisinfectTaskPageVO.OverdueBanner banner = new DisinfectTaskPageVO.OverdueBanner();
            banner.setShow(true);
            banner.setText("任务逾期后，系统会再提醒所有者和场长一次。已提醒你" + managerSuffix() + "。");
            vo.setOverdueBanner(banner);
        }
        return vo;
    }

    private Comparator<DisinfectTask> taskComparator() {
        return Comparator
                .comparing((DisinfectTask task) -> switch (taskStatus(task)) {
                    case HealthCodes.TASK_OVERDUE -> 0;
                    case HealthCodes.TASK_PENDING -> 1;
                    default -> 2;
                })
                .thenComparing(DisinfectTask::getTaskDate)
                .thenComparing(DisinfectTask::getId, Comparator.reverseOrder());
    }

    // ===== API-D37 任务下拉 =====

    @Override
    public List<DisinfectTaskOptionVO> taskOptions() {
        ensureTasks();
        LocalDate today = BeijingTime.today();
        List<DisinfectTask> tasks = taskMapper.selectList(Wrappers.<DisinfectTask>lambdaQuery()
                .in(DisinfectTask::getStatus, HealthCodes.TASK_PENDING, HealthCodes.TASK_OVERDUE)
                .orderByAsc(DisinfectTask::getTaskDate).orderByAsc(DisinfectTask::getId));
        List<DisinfectTaskOptionVO> list = new ArrayList<>();
        for (DisinfectTask task : tasks) {
            if (support.staffView() && !support.member().canAccessPen(task.getPenId())) {
                continue;
            }
            boolean overdue = HealthCodes.TASK_OVERDUE.equals(taskStatus(task))
                    || task.getTaskDate().isBefore(today);
            if (support.staffView() && (overdue || !task.getTaskDate().equals(today))) {
                continue;
            }
            if (!support.staffView() && !overdue && !task.getTaskDate().equals(today)) {
                continue;
            }
            DisinfectTaskOptionVO vo = new DisinfectTaskOptionVO();
            vo.setTaskId(task.getId());
            vo.setPenId(task.getPenId());
            vo.setPenName(support.penName(task.getPenId()));
            vo.setTaskDate(task.getTaskDate());
            vo.setDateText(HealthFormat.dateText(task.getTaskDate()));
            vo.setOverdue(overdue);
            vo.setPlanConcentration(task.getConcentration());
            vo.setPlanText(planText(task));
            vo.setMethodCode(task.getMethodCode());
            vo.setDrugId(planDrugId(task.getPlanId()));
            vo.setDefaultLotId(defaultLotId(vo.getDrugId()));
            if (support.managerView()) {
                vo.setDefaultExecutorId(support.defaultExecutorId(task.getPenId()));
            }
            list.add(vo);
        }
        list.sort(Comparator.comparing(DisinfectTaskOptionVO::getOverdue, Comparator.reverseOrder())
                .thenComparing(DisinfectTaskOptionVO::getTaskDate));
        return list;
    }

    private String planText(DisinfectTask task) {
        Integer cycle = cycleDays(task.getPlanId());
        return "计划：" + task.getDisinfectantName() + " " + task.getConcentration() + " · "
                + methodName(task.getMethodCode()) + (cycle == null ? "" : " · 每 " + cycle + " 天");
    }

    // ===== API-D38 登记消毒 =====

    @Override
    @Transactional
    public DisinfectRecordVO createRecord(DisinfectRecordCreateRequest request) {
        if (request == null || request.getRecordType() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择消毒类型");
        }
        String type = request.getRecordType().toUpperCase();
        if (!List.of(HealthCodes.RECORD_PLAN_TASK, HealthCodes.RECORD_EMPTY_PEN, HealthCodes.RECORD_EMERGENCY)
                .contains(type)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择消毒类型");
        }
        LocalDateTime actualTime = request.getActualTime() == null ? BeijingTime.now() : request.getActualTime();
        if (actualTime.isAfter(BeijingTime.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "实际时间不能晚于现在");
        }
        if (request.getUsageQty() == null || request.getUsageQty().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用量需大于 0");
        }
        if (HealthFormat.concentrationValue(request.getConcentration()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请按 1:500 的格式填写浓度");
        }
        if (request.getMethodCode() == null || request.getMethodCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择消毒方式");
        }
        DisinfectTask task = null;
        Long penId = request.getPenId();
        String planConcentration = null;
        boolean backfill = false;
        if (HealthCodes.RECORD_PLAN_TASK.equals(type)) {
            task = request.getTaskId() == null ? null : taskMapper.selectForUpdate(request.getTaskId());
            if (task == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请选择消毒任务");
            }
            penId = task.getPenId();
            support.checkPenAccess(penId);
            planConcentration = task.getConcentration();
            String taskStatus = taskStatus(task);
            if (HealthCodes.TASK_DONE.equals(taskStatus)) {
                throw new BizException(ErrorCode.STATE_CHANGED, "这条消毒任务已由他人登记，无需重复登记");
            }
            if (!HealthCodes.TASK_PENDING.equals(taskStatus) && !HealthCodes.TASK_OVERDUE.equals(taskStatus)) {
                throw new BizException(ErrorCode.STATE_CHANGED, "消毒任务已暂停或取消，请刷新后再登记");
            }
            if (HealthCodes.TASK_OVERDUE.equals(taskStatus) || task.getTaskDate().isBefore(BeijingTime.today())) {
                support.requireManager("逾期任务需所有者或场长补录");
                backfill = true;
            }
            if (!HealthFormat.sameConcentration(planConcentration, request.getConcentration())
                    && !Boolean.TRUE.equals(request.getConcentrationConfirmed())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请先确认本次浓度");
            }
        } else {
            if (penId == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请选择栏舍");
            }
            support.checkPenAccess(penId);
            if (!support.penEnabled(penId)) {
                throw new BizException(ErrorCode.PEN_DISABLED);
            }
        }
        FarmDrugRow drug = request.getDrugId() == null ? null : drugRows().get(request.getDrugId());
        if (drug == null || !HealthCodes.CATEGORY_DISINFECTANT.equals(drug.getCategory())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择消毒剂");
        }
        List<DrugLotOption> lots = support.lotsFor(drug.getId(), HealthCodes.PURPOSE_DISINFECT);
        DrugLotOption lot = support.findLot(lots, request.getDrugLotId());
        support.assertLotUsable(lot, request.getUsageQty(), drug.getName(),
                HealthCodes.unitText(drug.getUnit()));
        Long executorId = executor(request.getExecutorId(), penId);

        DisinfectRecord record = new DisinfectRecord();
        record.setFarmId(support.farmId());
        record.setPenId(penId);
        record.setRecordType(type);
        record.setTaskId(task == null ? null : task.getId());
        record.setPlanConcentration(planConcentration);
        record.setActualTime(actualTime);
        record.setExecutorId(executorId);
        record.setDrugId(drug.getId());
        record.setDisinfectantName(drug.getName());
        record.setDrugLotId(lot.lotId());
        record.setLotNo(lot.lotNo());
        record.setUsageQty(request.getUsageQty());
        record.setUsageUnit(drug.getUnit());
        record.setConcentration(request.getConcentration());
        record.setConcentrationConfirmed(Boolean.TRUE.equals(request.getConcentrationConfirmed()));
        record.setMethodCode(request.getMethodCode());
        record.setBackfill(backfill);
        record.setInBatchCost(!HealthCodes.RECORD_EMPTY_PEN.equals(type));
        record.setInExecRate(HealthCodes.RECORD_PLAN_TASK.equals(type));
        record.setClientRequestId(clientRequestId());
        record.setDeleted(false);
        record.setVersion(0);
        recordMapper.insert(record);
        consume(record);
        if (task != null) {
            int rows = taskMapper.update(null, Wrappers.<DisinfectTask>lambdaUpdate()
                    .eq(DisinfectTask::getId, task.getId())
                    .eq(DisinfectTask::getVersion, task.getVersion() == null ? 0 : task.getVersion())
                    .set(DisinfectTask::getStatus, HealthCodes.TASK_DONE)
                    .set(DisinfectTask::getRecordId, record.getId())
                    .set(DisinfectTask::getVersion, (task.getVersion() == null ? 0 : task.getVersion()) + 1));
            if (rows == 0) {
                throw new BizException(ErrorCode.STATE_CHANGED, "这条消毒任务已由他人登记，无需重复登记");
            }
            todoSource.completeDisinfect(task, record.getId(), executorId);
        }
        bindPhotos(record.getId(), request.getFileIds());
        audit.record(record.getFarmId(), "DISINFECT_RECORD", record.getId(), "CREATE", drug.getName(),
                null, null, Map.of("usage_qty", request.getUsageQty(), "concentration", request.getConcentration()));
        DisinfectRecordVO vo = toRecordVO(record, task);
        vo.setTodoCompleted(task != null);
        vo.setToast(HealthCodes.RECORD_EMPTY_PEN.equals(type) ? "已登记空栏消毒"
                : HealthCodes.RECORD_EMERGENCY.equals(type) ? "已登记紧急消毒" : "已登记消毒，待办已自动完成");
        return vo;
    }

    // ===== API-D39 记录详情 =====

    @Override
    public DisinfectRecordVO record(Long recordId) {
        DisinfectRecord record = requireRecord(recordId);
        if (support.staffView() && !support.member().canAccessPen(record.getPenId())) {
            throw new BizException(ErrorCode.PEN_FORBIDDEN, "你没有这个栏舍的查看权限");
        }
        DisinfectTask task = record.getTaskId() == null ? null : taskMapper.selectById(record.getTaskId());
        return toRecordVO(record, task);
    }

    // ===== API-D40 修改记录 =====

    @Override
    @Transactional
    public DisinfectRecordVO updateRecord(Long recordId, DisinfectRecordUpdateRequest request) {
        DisinfectRecord record = requireRecord(recordId);
        if (support.staffView() && !support.member().canAccessPen(record.getPenId())) {
            throw new BizException(ErrorCode.PEN_FORBIDDEN, "你没有这个栏舍的查看权限");
        }
        if (request == null || request.getVersion() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 version");
        }
        boolean sameDay = record.getCreatedAt() != null
                && record.getCreatedAt().toLocalDate().equals(BeijingTime.today());
        boolean mine = UserContext.userId() != null && UserContext.userId().equals(record.getCreatedBy());
        boolean canEdit = support.managerView()
                ? (record.getCreatedAt() != null
                && BeijingTime.withinEditWindow(record.getCreatedAt().toLocalDate(), 3))
                : (sameDay && mine);
        if (!canEdit) {
            throw new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED,
                    support.staffView() ? "消毒记录只能在登记当天修改" : "已超过可直接修改的时间，请提交修改申请")
                    .with("reason", "EXPIRED");
        }
        LocalDateTime actualTime = request.getActualTime() == null ? record.getActualTime() : request.getActualTime();
        if (actualTime.isAfter(BeijingTime.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "实际时间不能晚于现在");
        }
        BigDecimal usageQty = request.getUsageQty() == null ? record.getUsageQty() : request.getUsageQty();
        if (usageQty == null || usageQty.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用量需大于 0");
        }
        String concentration = request.getConcentration() == null
                ? record.getConcentration() : request.getConcentration();
        if (HealthFormat.concentrationValue(concentration) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请按 1:500 的格式填写浓度");
        }
        if (record.getPlanConcentration() != null
                && !HealthFormat.sameConcentration(record.getPlanConcentration(), concentration)
                && !Boolean.TRUE.equals(request.getConcentrationConfirmed())
                && !Boolean.TRUE.equals(record.getConcentrationConfirmed())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先确认本次浓度");
        }
        Long lotId = request.getDrugLotId() == null ? record.getDrugLotId() : request.getDrugLotId();
        List<DrugLotOption> lots = support.lotsFor(record.getDrugId(), HealthCodes.PURPOSE_DISINFECT);
        DrugLotOption lot = support.findLot(lots, lotId);
        // 原用量会在同一事务中回滚，不能用尚未回滚的结存校验完整新用量。
        support.assertLotUsable(lot, null, record.getDisinfectantName(), record.getUsageUnit());
        drugStockPort.restore(DrugStockCommand.BIZ_DISINFECT_RECORD, recordId);
        record.setActualTime(actualTime);
        record.setDrugLotId(lot.lotId());
        record.setLotNo(lot.lotNo());
        record.setUsageQty(usageQty);
        record.setConcentration(concentration);
        record.setConcentrationConfirmed(Boolean.TRUE.equals(request.getConcentrationConfirmed()));
        record.setMethodCode(request.getMethodCode() == null ? record.getMethodCode() : request.getMethodCode());
        if (request.getExecutorId() != null) {
            record.setExecutorId(executor(request.getExecutorId(), record.getPenId()));
        }
        record.setVersion(request.getVersion());
        int rows = recordMapper.updateById(record);
        if (rows == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        consume(record);
        bindPhotos(recordId, request.getFileIds());
        audit.record(record.getFarmId(), "DISINFECT_RECORD", recordId, "UPDATE", record.getDisinfectantName(),
                null, null, Map.of("usage_qty", usageQty, "concentration", concentration));
        return record(recordId);
    }

    // ===== 任务生成与取消 =====

    private int generate(DisinfectPlan plan, List<Long> penIds, LocalDate from, LocalDate to) {
        int count = taskGenerator.generate(plan, penIds, from, to);
        todoSource.generate(TodoSource.TodoSourceContext.builder().farmId(plan.getFarmId()).build());
        return count;
    }

    private void ensureTasks() {
        Long farmId = support.farmId();
        taskGenerator.ensureGenerated(farmId, BeijingTime.today());
        todoSource.generate(TodoSource.TodoSourceContext.builder().farmId(farmId).build());
    }

    private void consume(DisinfectRecord record) {
        // 空栏消毒仍扣药品库存；不给批次/栏舍分摊对象，避免错误计入批次成本。
        Long costPenId = Boolean.TRUE.equals(record.getInBatchCost()) ? record.getPenId() : null;
        StockConsumeResult result = drugStockPort.deduct(new DrugStockCommand(
                record.getFarmId(), record.getDrugId(), record.getDrugLotId(), record.getUsageQty(),
                DrugStockCommand.FLOW_DISINFECT_USE, DrugStockCommand.BIZ_DISINFECT_RECORD, record.getId(),
                null, costPenId, record.getExecutorId(), record.getActualTime(),
                record.getActualTime().toLocalDate(), record.getDisinfectantName()));
        recordMapper.update(null, Wrappers.<DisinfectRecord>lambdaUpdate()
                .eq(DisinfectRecord::getId, record.getId())
                .set(DisinfectRecord::getStockFlowId, result.flowId())
                .set(DisinfectRecord::getUnitPrice, result.unitPrice())
                .set(DisinfectRecord::getCostAmount, result.amount()));
        record.setStockFlowId(result.flowId());
        record.setUnitPrice(result.unitPrice());
        record.setCostAmount(result.amount());
    }

    private void cancelFrom(Long planId, LocalDate from, String reason) {
        taskMapper.update(null, Wrappers.<DisinfectTask>lambdaUpdate()
                .eq(DisinfectTask::getPlanId, planId)
                .ge(DisinfectTask::getTaskDate, from)
                .in(DisinfectTask::getStatus, HealthCodes.TASK_PENDING, HealthCodes.TASK_PAUSED)
                .set(DisinfectTask::getStatus, HealthCodes.TASK_CANCELLED)
                .set(DisinfectTask::getActiveFlag, null)
                .set(DisinfectTask::getCancelledAt, BeijingTime.now())
                .set(DisinfectTask::getCancelReason, reason));
    }

    // ===== 校验与组装 =====

    private void validate(DisinfectPlanSaveRequest request, DisinfectPlan existing) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写计划内容");
        }
        if (request.getPenIds() == null || request.getPenIds().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请至少选择 1 个栏舍");
        }
        for (Long penId : new LinkedHashSet<>(request.getPenIds())) {
            if (!support.penEnabled(penId)) {
                throw new BizException(ErrorCode.PEN_DISABLED);
            }
        }
        if (request.getCycleDays() == null || request.getCycleDays() < 1 || request.getCycleDays() > 365) {
            throw new BizException(ErrorCode.PARAM_INVALID, "周期天数需为大于 0 的整数");
        }
        if (request.getStartDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择开始日期");
        }
        boolean started = existing != null && !request.getStartDate().isAfter(BeijingTime.today());
        if (!started && request.getStartDate().isBefore(BeijingTime.today())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开始日期不能早于今天");
        }
        if (request.getEndDate() == null || request.getEndDate().isBefore(request.getStartDate())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) > 366) {
            throw new BizException(ErrorCode.PARAM_INVALID, "起止跨度不能超过 366 天");
        }
        if (request.getCatalogId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择消毒剂");
        }
        catalog(request.getCatalogId());
        if (HealthFormat.concentrationValue(request.getConcentration()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请按 1:500 的格式填写浓度");
        }
        if (request.getMethodCode() == null || request.getMethodCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择消毒方式");
        }
        if (request.getDueTime() != null && parseTime(request.getDueTime()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "截止时刻格式为 HH:mm");
        }
    }

    private DisinfectantCatalogRow catalog(Long catalogId) {
        for (DisinfectantCatalogRow row : referenceMapper.selectDisinfectantCatalogs()) {
            if (row.getId().equals(catalogId)) {
                return row;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "请选择消毒剂");
    }

    private DisinfectPlan requirePlan(Long planId) {
        DisinfectPlan plan = planId == null ? null : planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return plan;
    }

    private DisinfectRecord requireRecord(Long recordId) {
        DisinfectRecord record = recordId == null ? null : recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(ErrorCode.RECORD_DELETED, "该记录已被删除，无法查看");
        }
        return record;
    }

    private void savePens(Long planId, List<Long> penIds) {
        for (Long penId : penIds) {
            DisinfectPlanPen row = new DisinfectPlanPen();
            row.setFarmId(support.farmId());
            row.setPlanId(planId);
            row.setPenId(penId);
            planPenMapper.insert(row);
        }
    }

    private List<Long> penIdsOf(Long planId) {
        List<Long> ids = new ArrayList<>();
        for (DisinfectPlanPen row : planPenMapper.selectList(Wrappers.<DisinfectPlanPen>lambdaQuery()
                .eq(DisinfectPlanPen::getPlanId, planId).orderByAsc(DisinfectPlanPen::getId))) {
            ids.add(row.getPenId());
        }
        return ids;
    }

    private Map<Long, List<Long>> penIdsByPlan() {
        Map<Long, List<Long>> map = new LinkedHashMap<>();
        for (DisinfectPlanPen row : planPenMapper.selectList(Wrappers.<DisinfectPlanPen>lambdaQuery()
                .orderByAsc(DisinfectPlanPen::getId))) {
            map.computeIfAbsent(row.getPlanId(), key -> new ArrayList<>()).add(row.getPenId());
        }
        return map;
    }

    private DisinfectPlanVO toPlanVO(DisinfectPlan plan, List<Long> penIds) {
        DisinfectPlanVO vo = new DisinfectPlanVO();
        vo.setId(plan.getId());
        vo.setVersion(plan.getVersion());
        vo.setPenIds(penIds);
        vo.setCycleDays(plan.getCycleDays());
        vo.setStartDate(plan.getStartDate());
        vo.setEndDate(plan.getEndDate());
        vo.setDueTime(HealthFormat.timeText(plan.getDueTime()));
        vo.setDisinfectantName(plan.getDisinfectantName());
        vo.setConcentration(plan.getConcentration());
        vo.setMethodCode(plan.getMethodCode());
        vo.setMethodName(methodName(plan.getMethodCode()));
        vo.setStatus(plan.getStatus());
        vo.setStatusText(HealthCodes.disinfectPlanStatusText(plan.getStatus()));
        LocalDate next = nextTaskDate(plan.getId());
        vo.setNextTaskDate(next);
        vo.setNextTaskText(HealthFormat.dateText(next));
        vo.setPenNamesText(penNamesText(penIds));
        vo.setPauseText(pauseText(plan));
        List<String> actions = new ArrayList<>();
        if (HealthCodes.DISINFECT_PLAN_ACTIVE.equals(plan.getStatus())) {
            actions.add(HealthCodes.ACTION_PAUSE);
        } else if (HealthCodes.DISINFECT_PLAN_PAUSED.equals(plan.getStatus())) {
            actions.add(HealthCodes.ACTION_RESUME);
        }
        actions.add(HealthCodes.ACTION_EDIT);
        vo.setActions(actions);
        return vo;
    }

    private void copy(DisinfectPlanVO from, DisinfectPlanVO to) {
        to.setId(from.getId());
        to.setVersion(from.getVersion());
        to.setPenIds(from.getPenIds());
        to.setPenNamesText(from.getPenNamesText());
        to.setCycleDays(from.getCycleDays());
        to.setStartDate(from.getStartDate());
        to.setEndDate(from.getEndDate());
        to.setDueTime(from.getDueTime());
        to.setDisinfectantName(from.getDisinfectantName());
        to.setConcentration(from.getConcentration());
        to.setMethodCode(from.getMethodCode());
        to.setMethodName(from.getMethodName());
        to.setStatus(from.getStatus());
        to.setStatusText(from.getStatusText());
        to.setNextTaskDate(from.getNextTaskDate());
        to.setNextTaskText(from.getNextTaskText());
        to.setPauseText(from.getPauseText());
        to.setActions(from.getActions());
    }

    private String penNamesText(List<Long> penIds) {
        List<String> names = new ArrayList<>();
        Map<Long, PenInfo> pens = support.pens(penIds);
        for (Long penId : penIds) {
            PenInfo pen = pens.get(penId);
            names.add(pen == null ? support.penName(penId) : pen.penName());
        }
        if (names.size() <= 2) {
            return String.join("、", names);
        }
        return names.get(0) + "等 " + names.size() + " 个栏舍";
    }

    private String pauseText(DisinfectPlan plan) {
        if (!HealthCodes.DISINFECT_PLAN_PAUSED.equals(plan.getStatus())) {
            return null;
        }
        String by = support.userName(plan.getPausedBy());
        String reason = plan.getPauseReason() == null ? "" : plan.getPauseReason() + "，";
        return reason + (by == null ? "" : by) + "已暂停";
    }

    private LocalDate nextTaskDate(Long planId) {
        List<DisinfectTask> tasks = taskMapper.selectList(Wrappers.<DisinfectTask>lambdaQuery()
                .eq(DisinfectTask::getPlanId, planId)
                .eq(DisinfectTask::getStatus, HealthCodes.TASK_PENDING)
                .ge(DisinfectTask::getTaskDate, BeijingTime.today())
                .orderByAsc(DisinfectTask::getTaskDate).last("LIMIT 1"));
        return tasks.isEmpty() ? null : tasks.get(0).getTaskDate();
    }

    private DisinfectTaskVO toTaskVO(DisinfectTask task) {
        DisinfectTaskVO vo = new DisinfectTaskVO();
        vo.setId(task.getId());
        vo.setPlanId(task.getPlanId());
        vo.setPenId(task.getPenId());
        vo.setPenName(support.penName(task.getPenId()));
        vo.setTaskDate(task.getTaskDate());
        vo.setDateText(HealthFormat.dateText(task.getTaskDate()));
        vo.setDueTime(HealthFormat.timeText(task.getDueTime()));
        vo.setDisinfectantName(task.getDisinfectantName());
        vo.setConcentration(task.getConcentration());
        vo.setMethodName(methodName(task.getMethodCode()));
        vo.setCycleDays(cycleDays(task.getPlanId()));
        vo.setExecutorText(support.executorText(task.getPenId()));
        vo.setNoStaff(support.noPenStaff(task.getPenId()));
        vo.setSource(task.getSource());
        vo.setSourceText("PLAN".equals(task.getSource()) ? "计划生成" : "手动添加");
        String status = taskStatus(task);
        vo.setStatus(status);
        vo.setStatusText(HealthCodes.taskStatusText(status));
        vo.setManualDone(Boolean.TRUE.equals(task.getManualDone()));
        vo.setRecordId(task.getRecordId());
        if (task.getRecordId() != null) {
            DisinfectRecord record = recordMapper.selectById(task.getRecordId());
            if (record != null) {
                vo.setActualTime(record.getActualTime());
                vo.setActualExecutorName(support.userName(record.getExecutorId()));
            }
        }
        List<String> actions = new ArrayList<>();
        if (HealthCodes.TASK_PENDING.equals(status)) {
            actions.add(HealthCodes.ACTION_REGISTER);
        } else if (HealthCodes.TASK_OVERDUE.equals(status)) {
            if (support.managerView()) {
                actions.add(HealthCodes.ACTION_BACKFILL);
            }
        } else if (HealthCodes.TASK_DONE.equals(status)) {
            actions.add(HealthCodes.ACTION_VIEW_LOG);
        }
        vo.setActions(actions);
        return vo;
    }

    /** 已过截止时刻的 PENDING 派生 OVERDUE，存储值不变 */
    private String taskStatus(DisinfectTask task) {
        String status = task.getStatus();
        if (HealthCodes.TASK_PENDING.equals(status)) {
            LocalTime due = task.getDueTime() == null ? DEFAULT_DUE_TIME : task.getDueTime();
            if (LocalDateTime.of(task.getTaskDate(), due).isBefore(BeijingTime.now())) {
                return HealthCodes.TASK_OVERDUE;
            }
        }
        return status;
    }

    private DisinfectRecordVO toRecordVO(DisinfectRecord record, DisinfectTask task) {
        DisinfectRecordVO vo = new DisinfectRecordVO();
        vo.setId(record.getId());
        vo.setVersion(record.getVersion());
        vo.setRecordType(record.getRecordType());
        vo.setRecordTypeText(HealthCodes.recordTypeText(record.getRecordType()));
        vo.setTaskId(record.getTaskId());
        vo.setPlanConcentration(record.getPlanConcentration());
        vo.setPenId(record.getPenId());
        vo.setPenName(support.penName(record.getPenId()));
        vo.setActualTime(record.getActualTime());
        vo.setExecutorName(support.userName(record.getExecutorId()));
        vo.setCreatorName(support.userName(record.getCreatedBy()));
        vo.setDrugLotId(record.getDrugLotId());
        vo.setLotNo(record.getLotNo());
        vo.setDisinfectantName(record.getDisinfectantName());
        vo.setUsageQty(HealthFormat.plain(record.getUsageQty()));
        vo.setUsageUnit(HealthCodes.unitText(record.getUsageUnit()));
        vo.setConcentration(record.getConcentration());
        vo.setConcentrationConfirmed(Boolean.TRUE.equals(record.getConcentrationConfirmed()));
        vo.setMethodCode(record.getMethodCode());
        vo.setMethodName(methodName(record.getMethodCode()));
        vo.setInBatchCost(Boolean.TRUE.equals(record.getInBatchCost()));
        vo.setInExecRate(Boolean.TRUE.equals(record.getInExecRate()));
        vo.setBackfill(Boolean.TRUE.equals(record.getBackfill()));
        vo.setUnitPrice(record.getUnitPrice());
        vo.setCostAmount(record.getCostAmount());
        vo.setDrugId(record.getDrugId());
        vo.setExecutorId(record.getExecutorId());
        vo.setPhotos(bizMediaService.list(BizType.DISINFECT_RECORD, record.getId()));
        if (task != null) {
            vo.setTaskDate(task.getTaskDate());
            vo.setPlanText(planText(task));
        }
        vo.setChangeState(changeState(record, task));
        List<String> actions = new ArrayList<>();
        if (Boolean.TRUE.equals(vo.getChangeState().getCanEdit())) {
            actions.add(HealthCodes.ACTION_EDIT);
        } else if (support.managerView()) {
            actions.add(HealthCodes.ACTION_CHANGE_REQUEST);
        }
        actions.add(HealthCodes.ACTION_VIEW_LOG);
        vo.setActions(actions);
        return vo;
    }

    private ChangeRequestStateVO changeState(DisinfectRecord record, DisinfectTask task) {
        boolean sameDay = record.getCreatedAt() != null
                && record.getCreatedAt().toLocalDate().equals(BeijingTime.today());
        boolean mine = UserContext.userId() != null && UserContext.userId().equals(record.getCreatedBy());
        boolean canEdit = record.getCreatedAt() != null && (support.managerView()
                ? BeijingTime.withinEditWindow(record.getCreatedAt().toLocalDate(), 3)
                : sameDay && mine);
        ChangeRequestStateVO state = new ChangeRequestStateVO();
        state.setCanEdit(canEdit);
        state.setEditDeadline(canEdit
                ? BeijingTime.editDeadline(record.getCreatedAt().toLocalDate(), support.managerView() ? 3 : 0)
                .minusSeconds(1) : null);
        return state;
    }

    private void bindPhotos(Long recordId, List<Long> fileIds) {
        if (fileIds == null) {
            return;
        }
        bizMediaService.validateForBind(fileIds, FileScene.DISINFECT, UserContext.userId(), "file_ids",
                new com.zhimu.server.biz.common.api.BizRef(BizType.DISINFECT_RECORD, recordId));
        bizMediaService.replace(BizType.DISINFECT_RECORD, recordId, support.farmId(), fileIds);
    }

    private Long executor(Long requested, Long penId) {
        if (support.staffView()) {
            return UserContext.requireUserId();
        }
        if (requested != null) {
            for (var member : support.members()) {
                if (requested.equals(member.getUserId())) {
                    return requested;
                }
            }
        }
        Long defaultExecutor = support.defaultExecutorId(penId);
        return defaultExecutor == null ? UserContext.requireUserId() : defaultExecutor;
    }

    private Long planDrugId(Long planId) {
        if (planId == null) {
            return null;
        }
        DisinfectPlan plan = planMapper.selectById(planId);
        return plan == null ? null : plan.getDrugId();
    }

    private Long defaultLotId(Long drugId) {
        if (drugId == null) {
            return null;
        }
        for (DrugLotOption lot : support.lotsFor(drugId, HealthCodes.PURPOSE_DISINFECT)) {
            if (lot.lotStatus() == null || "NORMAL".equals(lot.lotStatus())
                    || "NEAR_EXPIRY".equals(lot.lotStatus())) {
                return lot.lotId();
            }
        }
        return null;
    }

    private Integer cycleDays(Long planId) {
        if (planId == null) {
            return null;
        }
        DisinfectPlan plan = planMapper.selectById(planId);
        return plan == null ? null : plan.getCycleDays();
    }

    private String methodName(String code) {
        return support.dictName("DISINFECT_METHOD", code);
    }

    private Map<Long, FarmDrugRow> drugRows() {
        Map<Long, FarmDrugRow> map = new LinkedHashMap<>();
        for (FarmDrugRow row : referenceMapper.selectDrugs(support.farmId())) {
            map.put(row.getId(), row);
        }
        return map;
    }

    private Map<Long, FarmDrugRow> drugsByCatalog() {
        Map<Long, FarmDrugRow> map = new LinkedHashMap<>();
        for (FarmDrugRow row : drugRows().values()) {
            if (HealthCodes.CATEGORY_DISINFECTANT.equals(row.getCategory())) {
                map.putIfAbsent(row.getCatalogId(), row);
            }
        }
        return map;
    }

    private FarmDrugRow drugByCatalog(Long catalogId) {
        return drugsByCatalog().get(catalogId);
    }

    private String clientRequestId() {
        String key = com.zhimu.server.common.context.RequestContext.idempotencyKey();
        return key == null || key.isBlank() ? "AUTO-" + java.util.UUID.randomUUID() : key;
    }

    private LocalTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return DEFAULT_DUE_TIME;
        }
        try {
            String value = text.trim();
            return LocalTime.parse(value.length() > 5 ? value.substring(0, 5) : value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase().contains(keyword.toLowerCase());
    }
}
