package com.zhimu.server.biz.health.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.DrugCatalogInfo;
import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.biz.health.entity.ImmunePlan;
import com.zhimu.server.biz.health.entity.ImmuneRecord;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.mapper.ImmunePlanMapper;
import com.zhimu.server.biz.health.mapper.ImmuneRecordMapper;
import com.zhimu.server.biz.health.query.FarmDrugRow;
import com.zhimu.server.biz.health.request.ImmuneRecordCreateRequest;
import com.zhimu.server.biz.health.request.ImmuneRecordUpdateRequest;
import com.zhimu.server.biz.health.service.ImmuneRecordService;
import com.zhimu.server.biz.health.support.HealthAudit;
import com.zhimu.server.biz.health.support.HealthCodes;
import com.zhimu.server.biz.health.support.HealthFormat;
import com.zhimu.server.biz.health.support.HealthSupport;
import com.zhimu.server.biz.health.vo.ImmuneRecordVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.stock.api.DrugLotOption;
import com.zhimu.server.biz.stock.api.DrugStockCommand;
import com.zhimu.server.biz.stock.api.DrugStockPort;
import com.zhimu.server.biz.stock.api.StockConsumeResult;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** API-D14–D16 接种记录实现：扣减 / 恢复库存（IMMUNE_RECORD），计划状态回写。 */
@Service
@RequiredArgsConstructor
public class ImmuneRecordServiceImpl implements ImmuneRecordService {

    private final ImmuneRecordMapper recordMapper;
    private final ImmunePlanMapper planMapper;
    private final HealthReferenceMapper referenceMapper;
    private final HealthSupport support;
    private final HealthAudit audit;
    private final DrugStockPort drugStockPort;
    private final DrugCatalogQueryService drugCatalogQueryService;
    private final HealthTodoSource todoSource;

    // ===== API-D14 登记接种 =====

    @Override
    @Transactional
    public ImmuneRecordVO create(ImmuneRecordCreateRequest request) {
        if (request == null || request.getPlanId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择免疫项目");
        }
        ImmunePlan plan = planMapper.selectById(request.getPlanId());
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        BatchInfo batch = plan.getBatchId() == null ? null : support.requireBatch(plan.getBatchId());
        Long penId = batch == null ? plan.getPenId() : support.mainPenId(batch);
        if (batch != null) {
            support.checkBatchAccess(batch);
            if (!batch.active()) {
                throw new BizException(ErrorCode.STATE_CHANGED, "批次已结束，未执行的计划已自动取消。");
            }
        } else {
            support.checkPenAccess(penId);
        }
        boolean manualDone = Boolean.TRUE.equals(plan.getManualDone());
        boolean allowed = List.of(HealthCodes.PLAN_PENDING, HealthCodes.PLAN_POSTPONED, HealthCodes.PLAN_MISSED)
                .contains(plan.getStatus())
                || (HealthCodes.PLAN_DONE.equals(plan.getStatus()) && manualDone);
        if (!allowed) {
            ImmuneRecord existing = plan.getRecordId() == null ? null : recordMapper.selectById(plan.getRecordId());
            String executor = existing == null ? null : support.userName(existing.getExecutorId());
            throw new BizException(ErrorCode.STATE_CHANGED,
                    "这项免疫已由" + (executor == null ? "他人" : executor) + "登记，无需重复登记");
        }
        boolean missed = HealthCodes.PLAN_MISSED.equals(plan.getStatus());
        if (support.staffView() && missed) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "漏免需所有者或场长补录");
        }
        int currentCount = batch != null ? (batch.currentCount() == null ? 0 : batch.currentCount())
                : (plan.getPenId() == null ? 0 : referenceMapper.countBreedingAnimals(plan.getFarmId(), plan.getPenId()));
        int quantity = request.getQuantity() == null ? 0 : request.getQuantity();
        if (quantity <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接种数量需大于 0");
        }
        CategoryInfo category = batch == null ? null : support.category(batch.categoryId());
        String unitText = category == null || category.countUnitText() == null ? "头" : category.countUnitText();
        if (quantity > currentCount) {
            throw new BizException(ErrorCode.COUNT_EXCEEDED, batch == null
                    ? "不能超过在档种畜 " + currentCount + " " + unitText
                    : "不能超过存栏 " + currentCount + " " + unitText);
        }
        BigDecimal dose = request.getDose() == null ? plan.getDose() : request.getDose();
        if (dose == null || dose.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "剂量需大于 0");
        }
        if (request.getMethodCode() == null || request.getMethodCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择接种方式");
        }
        LocalDate vaccinateDate = request.getVaccinateDate() == null ? BeijingTime.today() : request.getVaccinateDate();
        if (vaccinateDate.isAfter(BeijingTime.today())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接种日期不能晚于今天");
        }
        if (missed && vaccinateDate.isBefore(plan.getPlanDate())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "补免的接种日期不能早于计划日期");
        }
        FarmDrugRow drug = requireDrug(request.getDrugId(), plan);
        drugCatalogQueryService.assertNotBanned(plan.getCatalogId(),
                support.species(batch == null ? null : batch.categoryId()));
        BigDecimal totalDose = dose.multiply(BigDecimal.valueOf(quantity)).setScale(3, RoundingMode.HALF_UP);
        List<DrugLotOption> lots = support.lotsFor(drug.getId(), HealthCodes.PURPOSE_VACCINATE);
        DrugLotOption lot = support.findLot(lots, request.getDrugLotId());
        support.assertLotUsable(lot, totalDose, drug.getName(), HealthCodes.unitText(drug.getUnit()));
        Long executorId = executor(request.getExecutorId(), penId);

        ImmuneRecord record = new ImmuneRecord();
        record.setFarmId(support.farmId());
        record.setBatchId(plan.getBatchId());
        record.setPenId(penId);
        record.setPlanId(plan.getId());
        record.setDrugId(drug.getId());
        record.setVaccineName(plan.getVaccineName());
        record.setDrugLotId(lot.lotId());
        record.setLotNo(lot.lotNo());
        record.setLotExpireDate(lot.expireDate());
        record.setDose(dose);
        record.setDoseUnit(plan.getDoseUnit());
        record.setQuantity(quantity);
        record.setTotalDose(totalDose);
        record.setMethodCode(request.getMethodCode());
        record.setVaccinateDate(vaccinateDate);
        record.setExecutorId(executorId);
        record.setBackfill(missed);
        record.setClientRequestId(clientRequestId());
        record.setDeleted(false);
        record.setVersion(0);
        recordMapper.insert(record);
        consume(record, lot, totalDose, executorId, plan.getBatchId(), penId);
        completePlan(plan, record);
        todoSource.completeImmune(plan, record.getId(), executorId);
        audit.record(record.getFarmId(), "IMMUNE_RECORD", record.getId(), "UPDATE", planTarget(plan),
                support.batchNo(plan.getBatchId()), null,
                Map.of("quantity", quantity, "vaccinate_date", vaccinateDate.toString()));
        ImmuneRecordVO vo = toVO(record, plan, batch);
        vo.setTodoCompleted(true);
        vo.setOverdueDone(missed || isOverdue(plan));
        return vo;
    }

    // ===== API-D15 详情 =====

    @Override
    public ImmuneRecordVO detail(Long recordId) {
        ImmuneRecord record = requireRecord(recordId, ErrorCode.NOT_FOUND);
        checkAccess(record);
        ImmunePlan plan = planMapper.selectById(record.getPlanId());
        BatchInfo batch = record.getBatchId() == null ? null : support.requireBatch(record.getBatchId());
        return toVO(record, plan, batch);
    }

    // ===== API-D16 修改 =====

    @Override
    @Transactional
    public ImmuneRecordVO update(Long recordId, ImmuneRecordUpdateRequest request) {
        ImmuneRecord record = requireRecord(recordId, ErrorCode.NOT_FOUND);
        checkAccess(record);
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写要修改的内容");
        }
        if (request.getVersion() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 version");
        }
        boolean sameDay = record.getCreatedAt() != null
                && record.getCreatedAt().toLocalDate().equals(BeijingTime.today());
        boolean mine = UserContext.userId() != null && UserContext.userId().equals(record.getExecutorId());
        if (support.staffView() && !mine) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "只能修改自己登记的记录");
        }
        if (!sameDay) {
            throw new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED,
                    support.staffView() ? "接种记录只能在登记当天修改" : "已超过可直接修改的时间，请提交修改申请")
                    .with("reason", "EXPIRED");
        }
        ImmunePlan plan = planMapper.selectById(record.getPlanId());
        BatchInfo batch = record.getBatchId() == null ? null : support.requireBatch(record.getBatchId());
        BigDecimal dose = request.getDose() == null ? record.getDose() : request.getDose();
        if (dose == null || dose.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "剂量需大于 0");
        }
        int quantity = request.getQuantity() == null ? record.getQuantity() : request.getQuantity();
        int currentCount = batch != null ? (batch.currentCount() == null ? 0 : batch.currentCount())
                : (record.getPenId() == null ? 0
                : referenceMapper.countBreedingAnimals(record.getFarmId(), record.getPenId()));
        if (quantity <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接种数量需大于 0");
        }
        if (quantity > currentCount) {
            throw new BizException(ErrorCode.COUNT_EXCEEDED, "不能超过存栏 " + currentCount);
        }
        String methodCode = request.getMethodCode() == null ? record.getMethodCode() : request.getMethodCode();
        LocalDate vaccinateDate = request.getVaccinateDate() == null
                ? record.getVaccinateDate() : request.getVaccinateDate();
        if (vaccinateDate.isAfter(BeijingTime.today())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接种日期不能晚于今天");
        }
        FarmDrugRow drug = requireDrug(record.getDrugId(), plan);
        DrugLotOption lot;
        if (request.getDrugLotId() != null && !request.getDrugLotId().equals(record.getDrugLotId())) {
            List<DrugLotOption> lots = support.lotsFor(drug.getId(), HealthCodes.PURPOSE_VACCINATE);
            lot = support.findLot(lots, request.getDrugLotId());
        } else {
            lot = support.findLot(support.lotsFor(drug.getId(), HealthCodes.PURPOSE_VACCINATE),
                    record.getDrugLotId());
        }
        support.assertLotUsable(lot, null, drug.getName(), HealthCodes.unitText(drug.getUnit()));
        BigDecimal totalDose = dose.multiply(BigDecimal.valueOf(quantity)).setScale(3, RoundingMode.HALF_UP);

        // 差额调整：先回滚原流水，再按新数量扣减
        drugStockPort.restore(DrugStockCommand.BIZ_IMMUNE_RECORD, record.getId());
        record.setDrugLotId(lot.lotId());
        record.setLotNo(lot.lotNo());
        record.setLotExpireDate(lot.expireDate());
        record.setDose(dose);
        record.setQuantity(quantity);
        record.setTotalDose(totalDose);
        record.setMethodCode(methodCode);
        record.setVaccinateDate(vaccinateDate);
        if (request.getExecutorId() != null) {
            record.setExecutorId(executor(request.getExecutorId(), record.getPenId()));
        }
        record.setVersion(request.getVersion());
        int rows = recordMapper.updateById(record);
        if (rows == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        consume(record, lot, totalDose, record.getExecutorId(), record.getBatchId(), record.getPenId());
        audit.record(record.getFarmId(), "IMMUNE_RECORD", record.getId(), "UPDATE", planTarget(plan),
                support.batchNo(record.getBatchId()), null, Map.of("quantity", quantity));
        return detail(recordId);
    }

    // ===== 内部方法 =====

    private void consume(ImmuneRecord record, DrugLotOption lot, BigDecimal totalDose, Long executorId,
                         Long batchId, Long penId) {
        StockConsumeResult result = drugStockPort.deduct(new DrugStockCommand(
                record.getFarmId(), record.getDrugId(), lot.lotId(), totalDose,
                DrugStockCommand.FLOW_VACCINE_USE, DrugStockCommand.BIZ_IMMUNE_RECORD, record.getId(),
                batchId, penId, executorId, null, record.getVaccinateDate(), record.getVaccineName()));
        recordMapper.update(null, Wrappers.<ImmuneRecord>lambdaUpdate()
                .eq(ImmuneRecord::getId, record.getId())
                .set(ImmuneRecord::getStockFlowId, result.flowId())
                .set(ImmuneRecord::getUnitPrice, result.unitPrice())
                .set(ImmuneRecord::getCostAmount, result.amount()));
        record.setStockFlowId(result.flowId());
        record.setUnitPrice(result.unitPrice());
        record.setCostAmount(result.amount());
    }

    private void completePlan(ImmunePlan plan, ImmuneRecord record) {
        int version = plan.getVersion() == null ? 0 : plan.getVersion();
        int rows = planMapper.update(null, Wrappers.<ImmunePlan>lambdaUpdate()
                .eq(ImmunePlan::getId, plan.getId()).eq(ImmunePlan::getVersion, version)
                .set(ImmunePlan::getStatus, HealthCodes.PLAN_DONE)
                .set(ImmunePlan::getRecordId, record.getId())
                .set(ImmunePlan::getVersion, version + 1));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这项免疫已由他人登记，无需重复登记");
        }
    }

    private FarmDrugRow requireDrug(Long drugId, ImmunePlan plan) {
        FarmDrugRow drug = null;
        for (FarmDrugRow row : referenceMapper.selectDrugs(support.farmId())) {
            if (row.getId().equals(drugId)) {
                drug = row;
                break;
            }
        }
        if (drug == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择疫苗");
        }
        if (!HealthCodes.CATEGORY_VACCINE.equals(drug.getCategory())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择疫苗");
        }
        if (plan != null && plan.getCatalogId() != null && !plan.getCatalogId().equals(drug.getCatalogId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择计划对应的疫苗");
        }
        return drug;
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

    private String clientRequestId() {
        String key = RequestContext.idempotencyKey();
        return key == null || key.isBlank() ? "AUTO-" + UUID.randomUUID() : key;
    }

    private ImmuneRecord requireRecord(Long recordId, ErrorCode code) {
        ImmuneRecord record = recordId == null ? null : recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(code, code == ErrorCode.NOT_FOUND ? "记录不存在或无权查看" : code.getDefaultMessage());
        }
        return record;
    }

    private void checkAccess(ImmuneRecord record) {
        if (!support.managerView()) {
            if (support.staffView() && !support.member().canAccessPen(record.getPenId())) {
                throw new BizException(ErrorCode.PEN_FORBIDDEN, "你没有这个栏舍的查看权限");
            }
            if (record.getBatchId() != null) {
                BatchInfo batch = support.requireBatch(record.getBatchId());
                support.checkBatchAccess(batch);
            }
        }
    }

    private ImmuneRecordVO toVO(ImmuneRecord record, ImmunePlan plan, BatchInfo batch) {
        Map<Long, String> names = support.memberNames();
        ImmuneRecordVO vo = new ImmuneRecordVO();
        vo.setId(record.getId());
        vo.setPlanId(record.getPlanId());
        vo.setVersion(record.getVersion());
        vo.setBatchId(record.getBatchId());
        vo.setBatchNo(batch == null ? null : batch.batchNo());
        vo.setPenName(support.penName(record.getPenId()));
        vo.setVaccineName(record.getVaccineName());
        vo.setImmuneRound(plan == null ? null : plan.getImmuneRound());
        vo.setDrugLotId(record.getDrugLotId());
        vo.setLotNo(record.getLotNo());
        vo.setLotExpireDate(record.getLotExpireDate());
        vo.setDose(record.getDose());
        vo.setDoseUnit(HealthCodes.doseUnitText(record.getDoseUnit()));
        vo.setQuantity(record.getQuantity());
        vo.setTotalDose(HealthFormat.plain(record.getTotalDose()));
        vo.setMethodCode(record.getMethodCode());
        vo.setMethodName(support.dictName("VACCINATE_METHOD", record.getMethodCode()));
        vo.setVaccinateDate(record.getVaccinateDate());
        vo.setExecutorName(names.getOrDefault(record.getExecutorId(), support.userName(record.getExecutorId())));
        vo.setCreatorName(names.getOrDefault(record.getCreatedBy(), support.userName(record.getCreatedBy())));
        vo.setCreatedAt(record.getCreatedAt());
        vo.setBackfill(Boolean.TRUE.equals(record.getBackfill()));
        vo.setUnitPrice(record.getUnitPrice());
        vo.setCostAmount(record.getCostAmount());
        vo.setDrugId(record.getDrugId());
        vo.setExecutorId(record.getExecutorId());
        vo.setChangeState(changeState(record));
        vo.setActions(actions(record));
        if (!Boolean.TRUE.equals(vo.getChangeState().getCanEdit())) {
            vo.setEditHint(support.staffView() ? "接种记录只能在登记当天修改" : "已超过可直接修改的时间，请提交修改申请");
        }
        return vo;
    }

    private ChangeRequestStateVO changeState(ImmuneRecord record) {
        boolean sameDay = record.getCreatedAt() != null
                && record.getCreatedAt().toLocalDate().equals(BeijingTime.today());
        boolean mine = UserContext.userId() != null && UserContext.userId().equals(record.getExecutorId());
        boolean canEdit = sameDay && (support.managerView() || mine);
        ChangeRequestStateVO state = new ChangeRequestStateVO();
        state.setCanEdit(canEdit);
        state.setEditDeadline(canEdit && record.getCreatedAt() != null
                ? BeijingTime.editDeadline(record.getCreatedAt().toLocalDate(), 0).minusSeconds(1) : null);
        return state;
    }

    private List<String> actions(ImmuneRecord record) {
        List<String> actions = new ArrayList<>();
        if (Boolean.TRUE.equals(changeState(record).getCanEdit())) {
            actions.add(HealthCodes.ACTION_EDIT);
        } else if (support.managerView()) {
            actions.add(HealthCodes.ACTION_CHANGE_REQUEST);
        } else {
            actions.add(HealthCodes.ACTION_NOTIFY);
        }
        actions.add(HealthCodes.ACTION_VIEW_LOG);
        return actions;
    }

    private boolean isOverdue(ImmunePlan plan) {
        if (plan.getPlanDate() == null) {
            return false;
        }
        java.time.LocalTime due = plan.getDueTime() == null ? java.time.LocalTime.of(18, 0) : plan.getDueTime();
        return java.time.LocalDateTime.of(plan.getPlanDate(), due).isBefore(BeijingTime.now());
    }

    private String planTarget(ImmunePlan plan) {
        return plan.getVaccineName() + (plan.getImmuneRound() == null ? "" : " " + plan.getImmuneRound());
    }
}
